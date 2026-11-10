package com.app.sme_health_backend.inventory.ledger;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped persistence for the append-only movement ledger and its locked positions. */
@Repository
public class InventoryLedgerRepository {
    public static final int HISTORY_LIMIT = 500;

    static final RowMapper<InventoryMovementRecord> MOVEMENT = (rs, n) -> new InventoryMovementRecord(
            rs.getObject("id", UUID.class),
            rs.getObject("business_id", UUID.class),
            rs.getObject("item_id", UUID.class),
            rs.getLong("sequence_no"),
            InventoryMovementType.valueOf(rs.getString("movement_type")),
            rs.getString("purpose") == null ? null : InventoryPurpose.valueOf(rs.getString("purpose")),
            rs.getBigDecimal("quantity_delta"),
            rs.getBigDecimal("value_delta"),
            rs.getBigDecimal("quantity_after"),
            rs.getBigDecimal("value_after"),
            rs.getObject("movement_date", LocalDate.class),
            InventorySourceType.valueOf(rs.getString("source_type")),
            rs.getObject("source_id", UUID.class),
            rs.getObject("source_line_id", UUID.class),
            rs.getObject("project_id", UUID.class),
            rs.getObject("sample_id", UUID.class),
            rs.getObject("related_movement_id", UUID.class),
            rs.getString("idempotency_key"),
            rs.getString("payload_hash"),
            rs.getString("policy_version"),
            rs.getString("notes"),
            rs.getObject("created_by", UUID.class),
            rs.getObject("created_at", OffsetDateTime.class)
    );

    private static final RowMapper<InventoryPositionRecord> POSITION = (rs, n) -> new InventoryPositionRecord(
            rs.getObject("business_id", UUID.class),
            rs.getObject("item_id", UUID.class),
            rs.getBigDecimal("quantity"),
            rs.getBigDecimal("value"),
            rs.getLong("last_sequence_no"),
            rs.getObject("last_movement_date", LocalDate.class),
            rs.getLong("version")
    );

    private final JdbcTemplate jdbc;

    public InventoryLedgerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Locks the catalog row: serializes every movement of one item, including the very first one. */
    public boolean lockItem(UUID businessId, UUID itemId) {
        return !jdbc.queryForList("SELECT id FROM inventory_items WHERE business_id = ? AND id = ? FOR UPDATE",
                UUID.class, businessId, itemId).isEmpty();
    }

    public Optional<InventoryPositionRecord> position(UUID businessId, UUID itemId) {
        return jdbc.query("SELECT * FROM inventory_positions WHERE business_id = ? AND item_id = ?",
                POSITION, businessId, itemId).stream().findFirst();
    }

    public List<InventoryPositionRecord> positions(UUID businessId) {
        return jdbc.query("SELECT * FROM inventory_positions WHERE business_id = ?", POSITION, businessId);
    }

    public Optional<InventoryMovementRecord> byIdempotencyKey(UUID businessId, String key) {
        return jdbc.query("SELECT * FROM inventory_movements WHERE business_id = ? AND idempotency_key = ?",
                MOVEMENT, businessId, key).stream().findFirst();
    }

    public Optional<InventoryMovementRecord> movement(UUID businessId, UUID movementId) {
        return jdbc.query("SELECT * FROM inventory_movements WHERE business_id = ? AND id = ?",
                MOVEMENT, businessId, movementId).stream().findFirst();
    }

    public boolean isReversed(UUID businessId, UUID movementId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM inventory_movements WHERE business_id = ? "
                + "AND related_movement_id = ? AND movement_type = 'REVERSAL'", Integer.class, businessId, movementId);
        return count != null && count > 0;
    }

    /** Effective (non-reversed) returns recorded against one issue: [quantity, value] as positive numbers. */
    public BigDecimal[] effectiveReturns(UUID businessId, UUID issueId) {
        return jdbc.queryForObject("""
                SELECT COALESCE(sum(r.quantity_delta), 0), COALESCE(sum(r.value_delta), 0)
                FROM inventory_movements r
                WHERE r.business_id = ? AND r.related_movement_id = ? AND r.movement_type = 'CUSTOMER_RETURN'
                  AND NOT EXISTS (SELECT 1 FROM inventory_movements x WHERE x.business_id = r.business_id
                                  AND x.related_movement_id = r.id AND x.movement_type = 'REVERSAL')
                """, (rs, n) -> new BigDecimal[] {rs.getBigDecimal(1), rs.getBigDecimal(2)}, businessId, issueId);
    }

    /** Effective receipts already capitalized from one ledger transaction (value, positive). */
    public BigDecimal effectiveCapitalizedFromTransaction(UUID businessId, UUID transactionId) {
        return jdbc.queryForObject("""
                SELECT COALESCE(sum(m.value_delta), 0) FROM inventory_movements m
                WHERE m.business_id = ? AND m.source_type = 'TRANSACTION' AND m.source_id = ?
                  AND m.movement_type = 'RECEIPT'
                  AND NOT EXISTS (SELECT 1 FROM inventory_movements x WHERE x.business_id = m.business_id
                                  AND x.related_movement_id = m.id AND x.movement_type = 'REVERSAL')
                """, BigDecimal.class, businessId, transactionId);
    }

    /** Whether a commercial document line already has an effective (non-reversed) stock movement. */
    public boolean hasEffectiveLineMovement(UUID businessId, InventorySourceType sourceType, UUID lineId) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM inventory_movements m
                WHERE m.business_id = ? AND m.source_type = ? AND m.source_line_id = ?
                  AND m.movement_type IN ('RECEIPT', 'ISSUE')
                  AND NOT EXISTS (SELECT 1 FROM inventory_movements x WHERE x.business_id = m.business_id
                                  AND x.related_movement_id = m.id AND x.movement_type = 'REVERSAL')
                """, Integer.class, businessId, sourceType.name(), lineId);
        return count != null && count > 0;
    }

    public void insertMovement(InventoryMovementRecord m) {
        jdbc.update("""
                INSERT INTO inventory_movements(id, business_id, item_id, sequence_no, movement_type, purpose,
                    quantity_delta, value_delta, quantity_after, value_after, movement_date, source_type, source_id,
                    source_line_id, project_id, sample_id, related_movement_id, idempotency_key, payload_hash,
                    policy_version, notes, created_by, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                m.id(), m.businessId(), m.itemId(), m.sequenceNo(), m.movementType().name(),
                m.purpose() == null ? null : m.purpose().name(), m.quantityDelta(), m.valueDelta(), m.quantityAfter(),
                m.valueAfter(), Date.valueOf(m.movementDate()), m.sourceType().name(), m.sourceId(), m.sourceLineId(),
                m.projectId(), m.sampleId(), m.relatedMovementId(), m.idempotencyKey(), m.payloadHash(),
                m.policyVersion(), m.notes(), m.createdBy(), m.createdAt());
    }

    public void upsertPosition(UUID businessId, UUID itemId, BigDecimal quantity, BigDecimal value, long sequenceNo,
                               LocalDate movementDate, boolean exists) {
        if (exists) {
            jdbc.update("""
                    UPDATE inventory_positions SET quantity = ?, value = ?, last_sequence_no = ?, last_movement_date = ?,
                        version = version + 1, updated_at = now()
                    WHERE business_id = ? AND item_id = ?
                    """, quantity, value, sequenceNo, Date.valueOf(movementDate), businessId, itemId);
        } else {
            jdbc.update("""
                    INSERT INTO inventory_positions(business_id, item_id, quantity, value, last_sequence_no, last_movement_date)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, businessId, itemId, quantity, value, sequenceNo, Date.valueOf(movementDate));
        }
    }

    public List<InventoryMovementRecord> history(UUID businessId, UUID itemId, int limit) {
        return jdbc.query("SELECT * FROM inventory_movements WHERE business_id = ? AND item_id = ? "
                + "ORDER BY sequence_no DESC LIMIT ?", MOVEMENT, businessId, itemId, Math.min(limit, HISTORY_LIMIT));
    }

    public List<InventoryMovementRecord> movements(UUID businessId, LocalDate from, LocalDate to, int limit) {
        return jdbc.query("SELECT * FROM inventory_movements WHERE business_id = ? AND movement_date BETWEEN ? AND ? "
                        + "ORDER BY movement_date DESC, created_at DESC LIMIT ?", MOVEMENT, businessId,
                Date.valueOf(from), Date.valueOf(to), Math.min(limit, HISTORY_LIMIT));
    }

    public List<InventoryMovementRecord> bySource(UUID businessId, InventorySourceType sourceType, UUID sourceId) {
        return jdbc.query("SELECT * FROM inventory_movements WHERE business_id = ? AND source_type = ? AND source_id = ? "
                + "ORDER BY created_at", MOVEMENT, businessId, sourceType.name(), sourceId);
    }

    public List<InventoryMovementRecord> byProject(UUID businessId, UUID projectId) {
        return jdbc.query("SELECT * FROM inventory_movements WHERE business_id = ? AND project_id = ? ORDER BY created_at",
                MOVEMENT, businessId, projectId);
    }

    public List<InventoryMovementRecord> bySample(UUID businessId, UUID sampleId) {
        return jdbc.query("SELECT * FROM inventory_movements WHERE business_id = ? AND sample_id = ? ORDER BY created_at",
                MOVEMENT, businessId, sampleId);
    }

    /** Latest state on or before a date per item (movement dates are monotonic per item). */
    public List<Object[]> valuationAsOf(UUID businessId, LocalDate asOf) {
        return jdbc.query("""
                SELECT DISTINCT ON (m.item_id) m.item_id, m.quantity_after, m.value_after
                FROM inventory_movements m
                WHERE m.business_id = ? AND m.movement_date <= ?
                ORDER BY m.item_id, m.sequence_no DESC
                """, (rs, n) -> new Object[] {rs.getObject(1, UUID.class), rs.getBigDecimal(2), rs.getBigDecimal(3)},
                businessId, Date.valueOf(asOf));
    }

    /**
     * Net consumption value (positive = cost) per purpose in a date range: -sum(value_delta) over every
     * movement carrying a purpose (issues, returns and their reversals), so reversals and returns net exactly.
     */
    public List<Object[]> consumptionByPurpose(UUID businessId, LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT purpose, -COALESCE(sum(value_delta), 0), -COALESCE(sum(quantity_delta), 0)
                FROM inventory_movements
                WHERE business_id = ? AND purpose IS NOT NULL AND movement_date BETWEEN ? AND ?
                GROUP BY purpose
                """, (rs, n) -> new Object[] {rs.getString(1), rs.getBigDecimal(2), rs.getBigDecimal(3)},
                businessId, Date.valueOf(from), Date.valueOf(to));
    }

    /** Net adjustment-out losses (positive) in a range, including reversals of those adjustments. */
    public BigDecimal adjustmentLoss(UUID businessId, LocalDate from, LocalDate to) {
        return jdbc.queryForObject("""
                SELECT -COALESCE(sum(m.value_delta), 0) FROM inventory_movements m
                LEFT JOIN inventory_movements o ON o.business_id = m.business_id AND o.id = m.related_movement_id
                WHERE m.business_id = ? AND m.movement_date BETWEEN ? AND ?
                  AND (m.movement_type = 'ADJUSTMENT_OUT' OR (m.movement_type = 'REVERSAL' AND o.movement_type = 'ADJUSTMENT_OUT'))
                """, BigDecimal.class, businessId, Date.valueOf(from), Date.valueOf(to));
    }

    public BigDecimal sum(String sql, Object... args) {
        BigDecimal value = jdbc.queryForObject(sql, BigDecimal.class, args);
        return value == null ? BigDecimal.ZERO : value;
    }

    public JdbcTemplate jdbc() {
        return jdbc;
    }
}
