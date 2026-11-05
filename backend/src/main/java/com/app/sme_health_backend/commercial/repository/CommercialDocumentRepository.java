package com.app.sme_health_backend.commercial.repository;

import com.app.sme_health_backend.commercial.model.CommercialDocumentKind;
import com.app.sme_health_backend.commercial.model.CommercialDocumentRecord;
import com.app.sme_health_backend.commercial.model.CommercialDocumentRecord.LineRecord;
import com.app.sme_health_backend.commercial.model.CommercialDocumentStatus;
import com.app.sme_health_backend.commercial.service.CommercialDocumentCalculator.CalculatedLine;
import com.app.sme_health_backend.commercial.service.CommercialDocumentCalculator.DraftContent;
import com.app.sme_health_backend.commercial.service.CommercialException;
import com.app.sme_health_backend.crypto.SensitiveDataCipher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-scoped persistence for F04 Phase A. Every statement is bounded by business_id and kind.
 * The counterparty name is stored only as an AES-256-GCM envelope bound to {@link #COUNTERPARTY_AAD};
 * it is never searched, so encrypted storage never needs a plaintext index.
 */
@Repository
public class CommercialDocumentRepository {
    public static final String COUNTERPARTY_AAD = "FinSight|CommercialDocument|counterpartyName";
    public static final long MAX_DOCUMENT_NUMBER = 999_999L;
    private static final int LIST_LIMIT = 200;
    private static final String HEADER_SELECT = """
            SELECT d.*,
                   (SELECT count(*) FROM commercial_document_lines l
                    WHERE l.business_id = d.business_id AND l.document_id = d.id) AS line_count
            FROM commercial_documents d
            """;

    private static final RowMapper<LineRecord> LINE_ROW_MAPPER = (rs, rowNum) -> new LineRecord(
            rs.getObject("id", UUID.class),
            rs.getInt("line_number"),
            rs.getString("description"),
            rs.getBigDecimal("quantity"),
            rs.getString("unit"),
            rs.getBigDecimal("unit_price"),
            rs.getBigDecimal("line_amount")
    );

    private final JdbcTemplate jdbc;
    private final SensitiveDataCipher cipher;
    private final RowMapper<CommercialDocumentRecord> headerRowMapper;

    public CommercialDocumentRepository(JdbcTemplate jdbc, SensitiveDataCipher cipher) {
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.headerRowMapper = (rs, rowNum) -> {
            Date documentDate = rs.getDate("document_date");
            Date dueDate = rs.getDate("due_date");
            return new CommercialDocumentRecord(
                    rs.getObject("id", UUID.class),
                    rs.getObject("business_id", UUID.class),
                    CommercialDocumentKind.valueOf(rs.getString("kind")),
                    CommercialDocumentStatus.valueOf(rs.getString("status")),
                    rs.getString("document_number"),
                    cipher.decrypt(rs.getString("counterparty_name_ciphertext"), COUNTERPARTY_AAD),
                    documentDate != null ? documentDate.toLocalDate() : null,
                    dueDate != null ? dueDate.toLocalDate() : null,
                    rs.getString("notes"),
                    rs.getBigDecimal("subtotal"),
                    rs.getBigDecimal("total"),
                    rs.getString("base_currency").trim(),
                    rs.getString("financial_effect"),
                    rs.getString("calculation_version"),
                    rs.getLong("version"),
                    rs.getObject("created_by_user_id", UUID.class),
                    rs.getObject("updated_by_user_id", UUID.class),
                    rs.getObject("finalized_by_user_id", UUID.class),
                    rs.getObject("finalized_at", OffsetDateTime.class),
                    rs.getObject("voided_by_user_id", UUID.class),
                    rs.getObject("voided_at", OffsetDateTime.class),
                    rs.getString("void_reason"),
                    rs.getObject("created_at", OffsetDateTime.class),
                    rs.getObject("updated_at", OffsetDateTime.class),
                    rs.getInt("line_count"),
                    null
            );
        };
    }

    /** Row lock on the document itself; every mutation of one document is serialized here first. */
    public boolean lockForUpdate(UUID businessId, CommercialDocumentKind kind, UUID id) {
        return !jdbc.queryForList(
                "SELECT id FROM commercial_documents WHERE business_id = ? AND kind = ? AND id = ? FOR UPDATE",
                UUID.class, businessId, kind.name(), id
        ).isEmpty();
    }

    public Optional<CommercialDocumentRecord> findWithLines(UUID businessId, CommercialDocumentKind kind, UUID id) {
        return jdbc.query(HEADER_SELECT + " WHERE d.business_id = ? AND d.kind = ? AND d.id = ?",
                        headerRowMapper, businessId, kind.name(), id)
                .stream()
                .findFirst()
                .map(header -> header.withLines(findLines(businessId, id)));
    }

    public List<CommercialDocumentRecord> list(UUID businessId, CommercialDocumentKind kind, CommercialDocumentStatus status) {
        StringBuilder sql = new StringBuilder(HEADER_SELECT).append(" WHERE d.business_id = ? AND d.kind = ?");
        List<Object> params = new ArrayList<>(List.of(businessId, kind.name()));
        if (status != null) {
            sql.append(" AND d.status = ?");
            params.add(status.name());
        }
        sql.append(" ORDER BY d.updated_at DESC, d.id ASC LIMIT ").append(LIST_LIMIT);
        return jdbc.query(sql.toString(), headerRowMapper, params.toArray());
    }

    private List<LineRecord> findLines(UUID businessId, UUID documentId) {
        return jdbc.query("""
                SELECT id, line_number, description, quantity, unit, unit_price, line_amount
                FROM commercial_document_lines
                WHERE business_id = ? AND document_id = ?
                ORDER BY line_number
                """, LINE_ROW_MAPPER, businessId, documentId);
    }

    public void insertDraft(
            UUID id,
            UUID businessId,
            CommercialDocumentKind kind,
            DraftContent content,
            UUID actorUserId,
            OffsetDateTime now
    ) {
        jdbc.update("""
                INSERT INTO commercial_documents (
                    id, business_id, kind, status, counterparty_name_ciphertext, document_date, due_date, notes,
                    subtotal, total, version, created_by_user_id, updated_by_user_id, created_at, updated_at
                ) VALUES (?, ?, ?, 'DRAFT', ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?)
                """,
                id,
                businessId,
                kind.name(),
                encryptCounterparty(content.counterpartyName()),
                date(content.documentDate()),
                date(content.dueDate()),
                content.notes(),
                content.calculation().subtotal(),
                content.calculation().total(),
                actorUserId,
                actorUserId,
                now,
                now
        );
        insertLines(businessId, id, content.calculation().lines());
    }

    /** Optimistic DRAFT-only update; returns false when the version or status no longer matches. */
    public boolean updateDraft(
            UUID businessId,
            CommercialDocumentKind kind,
            UUID id,
            long expectedVersion,
            DraftContent content,
            UUID actorUserId,
            OffsetDateTime now
    ) {
        int rows = jdbc.update("""
                UPDATE commercial_documents
                SET counterparty_name_ciphertext = ?,
                    document_date = ?,
                    due_date = ?,
                    notes = ?,
                    subtotal = ?,
                    total = ?,
                    version = version + 1,
                    updated_by_user_id = ?,
                    updated_at = ?
                WHERE business_id = ? AND kind = ? AND id = ? AND version = ? AND status = 'DRAFT'
                """,
                encryptCounterparty(content.counterpartyName()),
                date(content.documentDate()),
                date(content.dueDate()),
                content.notes(),
                content.calculation().subtotal(),
                content.calculation().total(),
                actorUserId,
                now,
                businessId,
                kind.name(),
                id,
                expectedVersion
        );
        if (rows != 1) {
            return false;
        }
        jdbc.update("DELETE FROM commercial_document_lines WHERE business_id = ? AND document_id = ?", businessId, id);
        insertLines(businessId, id, content.calculation().lines());
        return true;
    }

    public boolean deleteDraft(UUID businessId, CommercialDocumentKind kind, UUID id, long expectedVersion) {
        return jdbc.update(
                "DELETE FROM commercial_documents WHERE business_id = ? AND kind = ? AND id = ? AND version = ? AND status = 'DRAFT'",
                businessId, kind.name(), id, expectedVersion
        ) == 1;
    }

    /**
     * Allocates the next number for business + kind under a row lock held until the caller's
     * finalization transaction ends, so a rollback also rolls back the allocation.
     */
    public long allocateNumber(UUID businessId, CommercialDocumentKind kind) {
        jdbc.update("""
                INSERT INTO commercial_number_sequences (business_id, kind)
                VALUES (?, ?)
                ON CONFLICT (business_id, kind) DO NOTHING
                """, businessId, kind.name());
        Long next = jdbc.queryForObject(
                "SELECT next_value FROM commercial_number_sequences WHERE business_id = ? AND kind = ? FOR UPDATE",
                Long.class, businessId, kind.name()
        );
        if (next == null || next > MAX_DOCUMENT_NUMBER) {
            throw new CommercialException(
                    "document_number_exhausted",
                    409,
                    "The six-digit document number range for this business is exhausted."
            );
        }
        jdbc.update("""
                UPDATE commercial_number_sequences
                SET next_value = next_value + 1, updated_at = now()
                WHERE business_id = ? AND kind = ?
                """, businessId, kind.name());
        return next;
    }

    public boolean finalizeDraft(
            UUID businessId,
            CommercialDocumentKind kind,
            UUID id,
            long expectedVersion,
            String documentNumber,
            UUID actorUserId,
            OffsetDateTime now
    ) {
        return jdbc.update("""
                UPDATE commercial_documents
                SET status = 'FINALIZED',
                    document_number = ?,
                    finalized_by_user_id = ?,
                    finalized_at = ?,
                    version = version + 1,
                    updated_by_user_id = ?,
                    updated_at = ?
                WHERE business_id = ? AND kind = ? AND id = ? AND version = ? AND status = 'DRAFT'
                """,
                documentNumber, actorUserId, now, actorUserId, now, businessId, kind.name(), id, expectedVersion
        ) == 1;
    }

    public boolean voidFinalized(
            UUID businessId,
            CommercialDocumentKind kind,
            UUID id,
            long expectedVersion,
            String reason,
            UUID actorUserId,
            OffsetDateTime now
    ) {
        return jdbc.update("""
                UPDATE commercial_documents
                SET status = 'VOID',
                    voided_by_user_id = ?,
                    voided_at = ?,
                    void_reason = ?,
                    version = version + 1,
                    updated_by_user_id = ?,
                    updated_at = ?
                WHERE business_id = ? AND kind = ? AND id = ? AND version = ? AND status = 'FINALIZED'
                """,
                actorUserId, now, reason, actorUserId, now, businessId, kind.name(), id, expectedVersion
        ) == 1;
    }

    private void insertLines(UUID businessId, UUID documentId, List<CalculatedLine> lines) {
        if (lines.isEmpty()) {
            return;
        }
        jdbc.batchUpdate("""
                INSERT INTO commercial_document_lines (
                    id, business_id, document_id, line_number, description, quantity, unit, unit_price, line_amount
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                lines.stream().map(line -> new Object[] {
                        UUID.randomUUID(),
                        businessId,
                        documentId,
                        line.lineNumber(),
                        line.description(),
                        line.quantity(),
                        line.unit(),
                        line.unitPrice(),
                        line.lineAmount()
                }).toList()
        );
    }

    private String encryptCounterparty(String counterpartyName) {
        return counterpartyName == null ? null : cipher.encrypt(counterpartyName, COUNTERPARTY_AAD);
    }

    private static Date date(java.time.LocalDate value) {
        return value == null ? null : Date.valueOf(value);
    }
}
