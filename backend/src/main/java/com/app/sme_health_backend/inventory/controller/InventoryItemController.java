package com.app.sme_health_backend.inventory.controller;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.inventory.dto.InventoryItemListResponse;
import com.app.sme_health_backend.inventory.dto.InventoryItemResponse;
import com.app.sme_health_backend.inventory.dto.ValuationPreviewResponse;
import com.app.sme_health_backend.inventory.model.InventoryItemKind;
import com.app.sme_health_backend.inventory.model.InventoryItemStatus;
import com.app.sme_health_backend.inventory.service.InventoryCatalogService;
import com.app.sme_health_backend.inventory.service.InventoryException;
import com.app.sme_health_backend.inventory.service.InventoryValidationException;
import com.app.sme_health_backend.inventory.service.InventoryValuationPreviewService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * F08 Phase A: inventory item catalog plus the stateless valuation preview.
 * Writes require RECORD_CREATE_UPDATE; reads and the non-persisting preview require FINANCIAL_DATA_READ
 * (the F06 estimate-preview precedent).
 */
@RestController
@RequestMapping("/api/inventory")
public class InventoryItemController {
    private final BusinessAuthorizationService authorization;
    private final InventoryCatalogService catalog;
    private final InventoryValuationPreviewService preview;

    public InventoryItemController(BusinessAuthorizationService authorization, InventoryCatalogService catalog,
                                   InventoryValuationPreviewService preview) {
        this.authorization = authorization;
        this.catalog = catalog;
        this.preview = preview;
    }

    @GetMapping(value = "/items", produces = "application/json")
    public ResponseEntity<InventoryItemListResponse> list(
            @RequestParam(value = "status", required = false) String statusParam,
            @RequestParam(value = "kind", required = false) String kindParam,
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "includeArchived", defaultValue = "false") boolean includeArchived,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        InventoryItemStatus status = parseEnumParam(InventoryItemStatus.class, statusParam, "status");
        InventoryItemKind kind = parseEnumParam(InventoryItemKind.class, kindParam, "kind");
        return noStore(ResponseEntity.ok()).body(catalog.list(context, status, kind, query, includeArchived));
    }

    @PostMapping(value = "/items", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> create(HttpServletRequest request) throws IOException {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.RECORD_CREATE_UPDATE);
        byte[] body = readBoundedBody(request);
        if (body == null) {
            return tooLarge();
        }
        InventoryItemResponse created = catalog.create(context, InventoryRequestReader.readCreate(body));
        return noStore(ResponseEntity.status(HttpStatus.CREATED)).body(created);
    }

    @GetMapping(value = "/items/{id}", produces = "application/json")
    public ResponseEntity<InventoryItemResponse> get(@PathVariable("id") UUID id, HttpServletRequest request) {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        return noStore(ResponseEntity.ok()).body(catalog.get(context, id));
    }

    @PutMapping(value = "/items/{id}", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> update(@PathVariable("id") UUID id, HttpServletRequest request) throws IOException {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.RECORD_CREATE_UPDATE);
        byte[] body = readBoundedBody(request);
        if (body == null) {
            return tooLarge();
        }
        return noStore(ResponseEntity.ok()).body(catalog.update(context, id, InventoryRequestReader.readUpdate(body)));
    }

    @PostMapping(value = "/items/{id}/archive", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> archive(@PathVariable("id") UUID id, HttpServletRequest request) throws IOException {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.RECORD_CREATE_UPDATE);
        byte[] body = readBoundedBody(request);
        if (body == null) {
            return tooLarge();
        }
        return noStore(ResponseEntity.ok()).body(catalog.archive(context, id, InventoryRequestReader.readArchive(body)));
    }

    /** PLANNING / VALUATION PREVIEW: stateless, authoritative=false, persists nothing. */
    @PostMapping(value = "/valuation/preview", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> valuationPreview(HttpServletRequest request) throws IOException {
        BusinessAccessContext context = authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        byte[] body = readBoundedBody(request);
        if (body == null) {
            return tooLarge();
        }
        ValuationPreviewResponse response = preview.preview(context.businessId(), InventoryRequestReader.readPreview(body));
        return noStore(ResponseEntity.ok()).body(response);
    }

    @ExceptionHandler(InventoryValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(InventoryValidationException ex) {
        return noStore(ResponseEntity.badRequest()).body(Map.of(
                "status", 400,
                "error", "inventory_invalid",
                "message", ex.getMessage(),
                "errors", ex.errors()
        ));
    }

    @ExceptionHandler(InventoryException.class)
    public ResponseEntity<Map<String, Object>> handleDomain(InventoryException ex) {
        return noStore(ResponseEntity.status(ex.status())).body(Map.of(
                "status", ex.status(),
                "error", ex.code(),
                "message", ex.getMessage()
        ));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String name = ex.getName() != null ? ex.getName() : "parameter";
        return noStore(ResponseEntity.badRequest()).body(Map.of(
                "status", 400,
                "error", "inventory_invalid",
                "message", "Invalid request parameter.",
                "errors", Map.of(name, "invalid")
        ));
    }

    private static ResponseEntity.BodyBuilder noStore(ResponseEntity.BodyBuilder builder) {
        return builder.header("Cache-Control", "no-store");
    }

    private static byte[] readBoundedBody(HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > InventoryRequestReader.MAX_REQUEST_BYTES) {
            return null;
        }
        // Read at most limit + 1 even when Content-Length is missing or dishonest.
        byte[] body = request.getInputStream().readNBytes(InventoryRequestReader.MAX_REQUEST_BYTES + 1);
        return body.length > InventoryRequestReader.MAX_REQUEST_BYTES ? null : body;
    }

    private static ResponseEntity<Map<String, Object>> tooLarge() {
        return noStore(ResponseEntity.status(413)).body(Map.of(
                "status", 413,
                "error", "inventory_request_too_large",
                "message", "The inventory request is too large."
        ));
    }

    private static <E extends Enum<E>> E parseEnumParam(Class<E> type, String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim());
        } catch (IllegalArgumentException ex) {
            throw new InventoryValidationException(field, "enum_invalid");
        }
    }
}
