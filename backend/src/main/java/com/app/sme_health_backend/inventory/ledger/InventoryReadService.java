package com.app.sme_health_backend.inventory.ledger;

import com.app.sme_health_backend.productsupport.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read side of the single inventory authority. Every other module (F04, F05, F09, reports, intelligence,
 * FinancialQueryFacade, dashboard) reads stock, valuation and COGS only through this service.
 */
@Service
public class InventoryReadService {
    private final InventoryLedgerRepository repository;

    public InventoryReadService(InventoryLedgerRepository repository) {
        this.repository = repository;
    }

    public record PositionView(
            UUID itemId, String sku, String name, String unit, String itemKind, String status,
            BigDecimal quantity, BigDecimal value, BigDecimal averageUnitCost, BigDecimal reorderLevel,
            boolean belowReorder, LocalDate lastMovementDate, boolean hasMovements
    ) { }

    public record Register(List<PositionView> items, BigDecimal totalValue, int itemsBelowReorder, int itemsWithStock,
                           String policyVersion) { }

    public record ConsumptionSummary(
            LocalDate from, LocalDate to,
            BigDecimal costOfGoodsSold,
            BigDecimal projectMaterial,
            BigDecimal sampleMaterial,
            BigDecimal internalConsumption,
            BigDecimal wastage,
            BigDecimal adjustmentLoss,
            BigDecimal soldQuantity,
            String policyVersion
    ) { }

    @Transactional(readOnly = true)
    public Register register(UUID businessId) {
        Map<UUID, InventoryPositionRecord> positions = new LinkedHashMap<>();
        for (InventoryPositionRecord p : repository.positions(businessId)) {
            positions.put(p.itemId(), p);
        }
        List<Map<String, Object>> items = repository.jdbc().queryForList(
                "SELECT id, sku, name, unit, item_kind, status, reorder_level FROM inventory_items "
                        + "WHERE business_id = ? ORDER BY status, sku LIMIT 1000", businessId);
        List<PositionView> views = new ArrayList<>();
        BigDecimal total = Money.ZERO;
        int below = 0;
        int withStock = 0;
        for (Map<String, Object> row : items) {
            UUID id = (UUID) row.get("id");
            InventoryPositionRecord p = positions.get(id);
            BigDecimal q = p == null ? BigDecimal.ZERO.setScale(6) : p.quantity();
            BigDecimal v = p == null ? Money.ZERO : p.value();
            BigDecimal reorder = (BigDecimal) row.get("reorder_level");
            boolean isBelow = reorder != null && "ACTIVE".equals(row.get("status")) && q.compareTo(reorder) <= 0;
            if (isBelow) below++;
            if (q.signum() > 0) withStock++;
            total = total.add(v);
            views.add(new PositionView(id, (String) row.get("sku"), (String) row.get("name"), (String) row.get("unit"),
                    (String) row.get("item_kind"), (String) row.get("status"), q, v, averageCost(q, v), reorder, isBelow,
                    p == null ? null : p.lastMovementDate(), p != null));
        }
        return new Register(views, total, below, withStock, InventoryLedgerService.POLICY_VERSION);
    }

    /** Total carrying value as of a date (null-safe: zero when nothing was ever recorded). */
    @Transactional(readOnly = true)
    public BigDecimal valueAsOf(UUID businessId, LocalDate asOf) {
        BigDecimal total = Money.ZERO;
        for (Object[] row : repository.valuationAsOf(businessId, asOf)) {
            total = total.add((BigDecimal) row[2]);
        }
        return total;
    }

    @Transactional(readOnly = true)
    public boolean hasAnyMovement(UUID businessId) {
        Integer count = repository.jdbc().queryForObject(
                "SELECT count(*) FROM inventory_movements WHERE business_id = ?", Integer.class, businessId);
        return count != null && count > 0;
    }

    @Transactional(readOnly = true)
    public ConsumptionSummary consumption(UUID businessId, LocalDate from, LocalDate to) {
        Map<InventoryPurpose, BigDecimal> value = new EnumMap<>(InventoryPurpose.class);
        BigDecimal soldQuantity = BigDecimal.ZERO;
        for (Object[] row : repository.consumptionByPurpose(businessId, from, to)) {
            InventoryPurpose purpose = InventoryPurpose.valueOf((String) row[0]);
            value.put(purpose, Money.nz((BigDecimal) row[1]));
            if (purpose == InventoryPurpose.SALE) {
                soldQuantity = (BigDecimal) row[2];
            }
        }
        return new ConsumptionSummary(from, to,
                value.getOrDefault(InventoryPurpose.SALE, Money.ZERO),
                value.getOrDefault(InventoryPurpose.PROJECT, Money.ZERO),
                value.getOrDefault(InventoryPurpose.SAMPLE, Money.ZERO),
                value.getOrDefault(InventoryPurpose.INTERNAL, Money.ZERO),
                value.getOrDefault(InventoryPurpose.WASTAGE, Money.ZERO),
                Money.nz(repository.adjustmentLoss(businessId, from, to)),
                soldQuantity, InventoryLedgerService.POLICY_VERSION);
    }

    /** Net material cost (positive) consumed by a project: PROJECT and project-tagged SALE movements. */
    @Transactional(readOnly = true)
    public BigDecimal projectMaterialCost(UUID businessId, UUID projectId) {
        return Money.nz(repository.sum("SELECT -COALESCE(sum(value_delta), 0) FROM inventory_movements "
                + "WHERE business_id = ? AND project_id = ? AND purpose IN ('PROJECT', 'SALE')", businessId, projectId));
    }

    /** Net material cost (positive) consumed by a sample. */
    @Transactional(readOnly = true)
    public BigDecimal sampleMaterialCost(UUID businessId, UUID sampleId) {
        return Money.nz(repository.sum("SELECT -COALESCE(sum(value_delta), 0) FROM inventory_movements "
                + "WHERE business_id = ? AND sample_id = ? AND purpose = 'SAMPLE'", businessId, sampleId));
    }

    /** Capitalized value of receipts sourced from a supplier bill (net of their reversals). */
    @Transactional(readOnly = true)
    public BigDecimal capitalizedFromBill(UUID businessId, UUID billId) {
        return Money.nz(repository.sum("""
                SELECT COALESCE(sum(m.value_delta), 0) FROM inventory_movements m
                WHERE m.business_id = ? AND m.source_type = 'SUPPLIER_BILL' AND m.source_id = ? AND m.movement_type = 'RECEIPT'
                  AND NOT EXISTS (SELECT 1 FROM inventory_movements x WHERE x.business_id = m.business_id
                                  AND x.related_movement_id = m.id AND x.movement_type = 'REVERSAL')
                """, businessId, billId));
    }

    /** Ledger payments (id -> capitalized value) whose amounts were capitalized into stock. */
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> capitalizedTransactions(UUID businessId) {
        Map<UUID, BigDecimal> result = new LinkedHashMap<>();
        repository.jdbc().query("""
                SELECT m.source_id, sum(m.value_delta) FROM inventory_movements m
                WHERE m.business_id = ? AND m.source_type = 'TRANSACTION' AND m.movement_type = 'RECEIPT'
                  AND NOT EXISTS (SELECT 1 FROM inventory_movements x WHERE x.business_id = m.business_id
                                  AND x.related_movement_id = m.id AND x.movement_type = 'REVERSAL')
                GROUP BY m.source_id
                """, rs -> {
            result.put(rs.getObject(1, UUID.class), rs.getBigDecimal(2));
        }, businessId);
        return result;
    }

    public static BigDecimal averageCost(BigDecimal quantity, BigDecimal value) {
        if (quantity == null || quantity.signum() == 0) {
            return null;
        }
        return value.divide(quantity, 6, RoundingMode.HALF_UP);
    }
}
