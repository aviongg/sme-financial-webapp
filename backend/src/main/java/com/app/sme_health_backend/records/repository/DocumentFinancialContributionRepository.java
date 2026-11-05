package com.app.sme_health_backend.records.repository;

import com.app.sme_health_backend.records.entity.DocumentFinancialContribution;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Repository
public class DocumentFinancialContributionRepository {
    private final JdbcTemplate jdbc;
    public DocumentFinancialContributionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public boolean hasFingerprint(UUID businessId, String fingerprint) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM document_financial_contributions WHERE business_id=? AND source_fingerprint=?)
                """, Boolean.class, businessId, fingerprint));
    }
    public Map<String, BigDecimal> floors(UUID businessId, String month) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(confirmed_amount) FILTER (WHERE target_classification='revenue'),0) AS revenue,
                       COALESCE(SUM(confirmed_amount) FILTER (WHERE target_classification='operating_expenses'),0) AS expenses,
                       COALESCE(SUM(confirmed_amount) FILTER (WHERE target_classification='cogs'),0) AS cogs,
                       COALESCE(SUM(confirmed_amount) FILTER (WHERE cash_flow_impact='cash_inflow'),0) AS inflow,
                       COALESCE(SUM(confirmed_amount) FILTER (WHERE cash_flow_impact='cash_outflow'),0) AS outflow
                FROM document_financial_contributions WHERE business_id=? AND target_month=?
                """, (rs, row) -> Map.of("revenue", rs.getBigDecimal("revenue"), "operatingExpenses", rs.getBigDecimal("expenses"),
                "cogs", rs.getBigDecimal("cogs"), "cashInflow", rs.getBigDecimal("inflow"), "cashOutflow", rs.getBigDecimal("outflow")),
                businessId, month);
    }
    public void insert(DocumentFinancialContribution contribution) {
        jdbc.update("""
                INSERT INTO document_financial_contributions
                (id,business_id,document_id,monthly_record_id,target_month,target_classification,cash_flow_impact,
                 confirmed_amount,actor_user_id,source_fingerprint,duplicate_override)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """, contribution.id(), contribution.businessId(), contribution.documentId(), contribution.monthlyRecordId(),
                contribution.targetMonth(), contribution.targetClassification(), contribution.cashFlowImpact(),
                contribution.confirmedAmount(), contribution.actorUserId(), contribution.sourceFingerprint(), contribution.duplicateOverride());
    }
}