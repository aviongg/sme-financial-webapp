package com.app.sme_health_backend.inventory.repository;

import com.app.sme_health_backend.inventory.model.InventoryItemKind;
import com.app.sme_health_backend.inventory.model.InventoryItemRecord;
import com.app.sme_health_backend.inventory.model.InventoryItemStatus;
import com.app.sme_health_backend.inventory.service.InventoryException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped catalog persistence. Every query is bound to the server-resolved business ID. */
@Repository
public class InventoryItemRepository {
    public static final int LIST_LIMIT = 500;

    private static final RowMapper<InventoryItemRecord> ROW_MAPPER = (rs, rowNum) -> new InventoryItemRecord(
            rs.getObject("id", UUID.class),
            rs.getObject("business_id", UUID.class),
            rs.getString("sku"),
            rs.getString("name"),
            rs.getString("unit"),
            InventoryItemKind.valueOf(rs.getString("item_kind")),
            rs.getString("category"),
            rs.getBigDecimal("reorder_level"),
            InventoryItemStatus.valueOf(rs.getString("status")),
            rs.getLong("version"),
            rs.getObject("created_by", UUID.class),
            rs.getObject("updated_by", UUID.class),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class)
    );

    private final JdbcTemplate jdbc;

    public InventoryItemRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Serializes SKU uniqueness checks per business, as F05 does for project codes. */
    public void lockActiveBusiness(UUID businessId) {
        List<UUID> locked = jdbc.queryForList(
                "SELECT id FROM businesses WHERE id = ? AND status = 'ACTIVE' FOR UPDATE",
                UUID.class,
                businessId
        );
        if (locked.isEmpty()) {
            throw new InventoryException("business_unavailable", 403, "The active business is unavailable.");
        }
    }

    public Optional<InventoryItemRecord> findById(UUID businessId, UUID itemId) {
        return jdbc.query(
                "SELECT * FROM inventory_items WHERE business_id = ? AND id = ?",
                ROW_MAPPER,
                businessId,
                itemId
        ).stream().findFirst();
    }

    public Optional<InventoryItemRecord> findForUpdate(UUID businessId, UUID itemId) {
        return jdbc.query(
                "SELECT * FROM inventory_items WHERE business_id = ? AND id = ? FOR UPDATE",
                ROW_MAPPER,
                businessId,
                itemId
        ).stream().findFirst();
    }

    public boolean existsBySku(UUID businessId, String sku, UUID excludeItemId) {
        Integer count = excludeItemId == null
                ? jdbc.queryForObject(
                        "SELECT count(*) FROM inventory_items WHERE business_id = ? AND sku = ?",
                        Integer.class, businessId, sku)
                : jdbc.queryForObject(
                        "SELECT count(*) FROM inventory_items WHERE business_id = ? AND sku = ? AND id <> ?",
                        Integer.class, businessId, sku, excludeItemId);
        return count != null && count > 0;
    }

    public List<InventoryItemRecord> findAllByBusiness(
            UUID businessId,
            InventoryItemStatus status,
            InventoryItemKind kind,
            String query,
            boolean includeArchived
    ) {
        StringBuilder sql = new StringBuilder("SELECT * FROM inventory_items WHERE business_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(businessId);
        if (status != null) {
            sql.append(" AND status = ?");
            params.add(status.name());
        } else if (!includeArchived) {
            sql.append(" AND status <> 'ARCHIVED'");
        }
        if (kind != null) {
            sql.append(" AND item_kind = ?");
            params.add(kind.name());
        }
        if (query != null && !query.isBlank()) {
            String pattern = "%" + escapeLike(query.trim().toLowerCase(Locale.ROOT)) + "%";
            sql.append(" AND (LOWER(sku) LIKE ? ESCAPE '\\' OR LOWER(name) LIKE ? ESCAPE '\\'"
                    + " OR LOWER(COALESCE(category, '')) LIKE ? ESCAPE '\\')");
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }
        sql.append(" ORDER BY sku ASC, id ASC LIMIT ").append(LIST_LIMIT);
        return jdbc.query(sql.toString(), ROW_MAPPER, params.toArray());
    }

    public void insert(InventoryItemRecord item) {
        jdbc.update("""
                INSERT INTO inventory_items (
                    id, business_id, sku, name, unit, item_kind, category, reorder_level,
                    status, version, created_by, updated_by, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                item.id(),
                item.businessId(),
                item.sku(),
                item.name(),
                item.unit(),
                item.itemKind().name(),
                item.category(),
                item.reorderLevel(),
                item.status().name(),
                item.version(),
                item.createdBy(),
                item.updatedBy(),
                item.createdAt(),
                item.updatedAt()
        );
    }

    public boolean updateCatalog(UUID businessId, UUID itemId, long expectedVersion, InventoryItemRecord values,
                                 UUID updatedBy, OffsetDateTime updatedAt) {
        return jdbc.update("""
                UPDATE inventory_items
                SET sku = ?, name = ?, unit = ?, item_kind = ?, category = ?, reorder_level = ?,
                    version = version + 1, updated_by = ?, updated_at = ?
                WHERE business_id = ? AND id = ? AND version = ? AND status = 'ACTIVE'
                """,
                values.sku(),
                values.name(),
                values.unit(),
                values.itemKind().name(),
                values.category(),
                values.reorderLevel(),
                updatedBy,
                updatedAt,
                businessId,
                itemId,
                expectedVersion
        ) == 1;
    }

    public boolean archive(UUID businessId, UUID itemId, long expectedVersion, UUID updatedBy, OffsetDateTime updatedAt) {
        return jdbc.update("""
                UPDATE inventory_items
                SET status = 'ARCHIVED', version = version + 1, updated_by = ?, updated_at = ?
                WHERE business_id = ? AND id = ? AND version = ? AND status = 'ACTIVE'
                """,
                updatedBy,
                updatedAt,
                businessId,
                itemId,
                expectedVersion
        ) == 1;
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
