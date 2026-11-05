package com.app.sme_health_backend.inventory.service;

import com.app.sme_health_backend.audit.model.AuditEventType;
import com.app.sme_health_backend.audit.service.SecurityAuditService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.inventory.dto.InventoryItemArchiveRequest;
import com.app.sme_health_backend.inventory.dto.InventoryItemListResponse;
import com.app.sme_health_backend.inventory.dto.InventoryItemRequest;
import com.app.sme_health_backend.inventory.dto.InventoryItemResponse;
import com.app.sme_health_backend.inventory.model.InventoryItemKind;
import com.app.sme_health_backend.inventory.model.InventoryItemRecord;
import com.app.sme_health_backend.inventory.model.InventoryItemStatus;
import com.app.sme_health_backend.inventory.repository.InventoryItemRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * F08 Phase A inventory item catalog. Items are identity only: creating, editing or archiving an item
 * creates no stock, movement, value, COGS or accounting effect, and touches no other domain table.
 */
@Service
public class InventoryCatalogService {
    public static final int MAX_SKU_LENGTH = 40;
    public static final int MAX_NAME_LENGTH = 160;
    public static final int MAX_UNIT_LENGTH = 40;
    public static final int MAX_CATEGORY_LENGTH = 80;
    private static final Pattern SKU_PATTERN = Pattern.compile("[A-Z0-9][A-Z0-9._/-]{0,39}");
    private static final String NOT_FOUND = "Inventory item not found.";
    private static final String STALE = "This inventory item changed since you loaded it. Reload before saving.";
    private static final String DUPLICATE = "An inventory item with this SKU already exists.";

    private final InventoryItemRepository repository;
    private final SecurityAuditService audit;

    public InventoryCatalogService(InventoryItemRepository repository, SecurityAuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    record ValidatedItem(String sku, String name, String unit, InventoryItemKind itemKind, String category,
                         BigDecimal reorderLevel) { }

    @Transactional
    public InventoryItemResponse create(BusinessAccessContext context, InventoryItemRequest request) {
        ValidatedItem item = validate(request);
        repository.lockActiveBusiness(context.businessId());
        if (repository.existsBySku(context.businessId(), item.sku(), null)) {
            throw new InventoryException("duplicate_inventory_sku", 409, DUPLICATE);
        }
        OffsetDateTime now = OffsetDateTime.now();
        InventoryItemRecord record = new InventoryItemRecord(UUID.randomUUID(), context.businessId(), item.sku(),
                item.name(), item.unit(), item.itemKind(), item.category(), item.reorderLevel(),
                InventoryItemStatus.ACTIVE, 0L, context.userId(), context.userId(), now, now);
        try {
            repository.insert(record);
        } catch (DataIntegrityViolationException exception) {
            throw new InventoryException("duplicate_inventory_sku", 409, DUPLICATE);
        }
        auditEvent(AuditEventType.INVENTORY_ITEM_CREATED, context, record);
        return InventoryItemResponse.fromRecord(record);
    }

    @Transactional(readOnly = true)
    public InventoryItemListResponse list(BusinessAccessContext context, InventoryItemStatus status,
                                          InventoryItemKind kind, String query, boolean includeArchived) {
        if (query != null && query.length() > MAX_NAME_LENGTH) {
            throw new InventoryValidationException("q", "text_invalid");
        }
        var items = repository.findAllByBusiness(context.businessId(), status, kind, query, includeArchived)
                .stream().map(InventoryItemResponse::fromRecord).toList();
        return new InventoryItemListResponse(context.businessId(), true, InventoryItemResponse.STOCK_TRACKING_STATUS, items);
    }

    @Transactional(readOnly = true)
    public InventoryItemResponse get(BusinessAccessContext context, UUID itemId) {
        return InventoryItemResponse.fromRecord(repository.findById(context.businessId(), itemId)
                .orElseThrow(InventoryCatalogService::notFound));
    }

    @Transactional
    public InventoryItemResponse update(BusinessAccessContext context, UUID itemId, InventoryItemRequest request) {
        ValidatedItem item = validate(request);
        long expectedVersion = requireVersion(request.expectedVersion());
        repository.lockActiveBusiness(context.businessId());
        InventoryItemRecord current = repository.findForUpdate(context.businessId(), itemId)
                .orElseThrow(InventoryCatalogService::notFound);
        requireActive(current);
        if (current.version() != expectedVersion) {
            throw new InventoryException("stale_inventory_item_version", 409, STALE);
        }
        if (repository.existsBySku(context.businessId(), item.sku(), itemId)) {
            throw new InventoryException("duplicate_inventory_sku", 409, DUPLICATE);
        }
        InventoryItemRecord values = new InventoryItemRecord(itemId, context.businessId(), item.sku(), item.name(),
                item.unit(), item.itemKind(), item.category(), item.reorderLevel(), current.status(),
                current.version(), current.createdBy(), context.userId(), current.createdAt(), current.updatedAt());
        boolean updated;
        try {
            updated = repository.updateCatalog(context.businessId(), itemId, expectedVersion, values,
                    context.userId(), OffsetDateTime.now());
        } catch (DataIntegrityViolationException exception) {
            throw new InventoryException("duplicate_inventory_sku", 409, DUPLICATE);
        }
        if (!updated) {
            throw new InventoryException("stale_inventory_item_version", 409, STALE);
        }
        InventoryItemRecord reloaded = repository.findById(context.businessId(), itemId).orElseThrow();
        auditEvent(AuditEventType.INVENTORY_ITEM_UPDATED, context, reloaded);
        return InventoryItemResponse.fromRecord(reloaded);
    }

    @Transactional
    public InventoryItemResponse archive(BusinessAccessContext context, UUID itemId, InventoryItemArchiveRequest request) {
        long expectedVersion = requireVersion(request == null ? null : request.expectedVersion());
        InventoryItemRecord current = repository.findForUpdate(context.businessId(), itemId)
                .orElseThrow(InventoryCatalogService::notFound);
        requireActive(current);
        if (current.version() != expectedVersion
                || !repository.archive(context.businessId(), itemId, expectedVersion, context.userId(), OffsetDateTime.now())) {
            throw new InventoryException("stale_inventory_item_version", 409, STALE);
        }
        InventoryItemRecord reloaded = repository.findById(context.businessId(), itemId).orElseThrow();
        auditEvent(AuditEventType.INVENTORY_ITEM_ARCHIVED, context, reloaded);
        return InventoryItemResponse.fromRecord(reloaded);
    }

    ValidatedItem validate(InventoryItemRequest request) {
        if (request == null) {
            throw new InventoryValidationException("request", "required");
        }
        String sku = normalizeSku(request.sku());
        String name = requiredText(request.name(), "name", MAX_NAME_LENGTH);
        String unit = requiredText(request.unit(), "unit", MAX_UNIT_LENGTH);
        if (request.itemKind() == null) {
            throw new InventoryValidationException("itemKind", "required");
        }
        String category = optionalText(request.category(), "category", MAX_CATEGORY_LENGTH);
        BigDecimal reorderLevel = request.reorderLevel() == null || request.reorderLevel().isBlank()
                ? null
                : InventoryDecimals.parseQuantity(request.reorderLevel(), "reorderLevel");
        return new ValidatedItem(sku, name, unit, request.itemKind(), category, reorderLevel);
    }

    /** SKUs are case-insensitive: they are trimmed and stored upper-cased (F05 project-code precedent). */
    static String normalizeSku(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InventoryValidationException("sku", "required");
        }
        String sku = raw.strip().toUpperCase(Locale.ROOT);
        if (sku.length() > MAX_SKU_LENGTH || !SKU_PATTERN.matcher(sku).matches()) {
            throw new InventoryValidationException("sku", "sku_invalid");
        }
        return sku;
    }

    private static String requiredText(String value, String path, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new InventoryValidationException(path, "required");
        }
        return boundedText(value, path, maxLength);
    }

    private static String optionalText(String value, String path, int maxLength) {
        return value == null || value.isBlank() ? null : boundedText(value, path, maxLength);
    }

    private static String boundedText(String value, String path, int maxLength) {
        String stripped = value.strip();
        if (stripped.length() > maxLength || stripped.codePoints().anyMatch(Character::isISOControl)) {
            throw new InventoryValidationException(path, "text_invalid");
        }
        return stripped;
    }

    private static long requireVersion(Long expectedVersion) {
        if (expectedVersion == null || expectedVersion < 0) {
            throw new InventoryValidationException("expectedVersion", "version_invalid");
        }
        return expectedVersion;
    }

    private static void requireActive(InventoryItemRecord item) {
        if (item.status() != InventoryItemStatus.ACTIVE) {
            throw new InventoryException("invalid_inventory_item_state", 409, "Archived inventory items cannot be changed.");
        }
    }

    /** Tenant mismatch and absence are indistinguishable so existence never leaks across businesses. */
    private static InventoryException notFound() {
        return new InventoryException("inventory_item_not_found", 404, NOT_FOUND);
    }

    private void auditEvent(AuditEventType type, BusinessAccessContext context, InventoryItemRecord item) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sku", item.sku());
        metadata.put("itemKind", item.itemKind().name());
        metadata.put("status", item.status().name());
        metadata.put("version", item.version());
        audit.logSuccess(type, context.userId(), null, context.businessId(), "inventory_item", item.id().toString(), metadata);
    }
}
