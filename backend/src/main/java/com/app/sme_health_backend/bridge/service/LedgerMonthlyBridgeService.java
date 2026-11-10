package com.app.sme_health_backend.bridge.service;

import com.app.sme_health_backend.audit.model.AuditEventType;
import com.app.sme_health_backend.audit.service.SecurityAuditService;
import com.app.sme_health_backend.bridge.dto.LedgerCompletenessReport;
import com.app.sme_health_backend.bridge.dto.LedgerCutoverRequest;
import com.app.sme_health_backend.bridge.dto.LedgerCutoverResponse;
import com.app.sme_health_backend.bridge.dto.LedgerMonthlyProjectionResponse;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.obligation.FinancialObligation;
import com.app.sme_health_backend.obligation.FinancialObligationRepository;
import com.app.sme_health_backend.obligation.ObligationPaymentAllocationRepository;
import com.app.sme_health_backend.obligation.ObligationStatus;
import com.app.sme_health_backend.obligation.aging.ObligationDirection;
import com.app.sme_health_backend.records.dto.MonthlyRecordResponse;
import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.records.repository.DocumentFinancialContributionRepository;
import com.app.sme_health_backend.records.repository.MonthlyRecordRepository;
import com.app.sme_health_backend.records.service.MonthlyRecordService;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class LedgerMonthlyBridgeService {

    public static final String BRIDGE_PROVENANCE = "LEDGER_MONTHLY_BRIDGE_V1";
    public static final String CALCULATION_VERSION = "c02-bridge-v1";

    private final JdbcTemplate jdbc;
    private final MonthlyRecordRepository monthlyRecordRepository;
    private final MonthlyRecordService monthlyRecordService;
    private final FinancialObligationRepository obligationRepository;
    private final ObligationPaymentAllocationRepository allocationRepository;
    private final DocumentFinancialContributionRepository contributions;
    private final SecurityAuditService auditService;

    public LedgerMonthlyBridgeService(
            JdbcTemplate jdbc,
            MonthlyRecordRepository monthlyRecordRepository,
            MonthlyRecordService monthlyRecordService,
            FinancialObligationRepository obligationRepository,
            ObligationPaymentAllocationRepository allocationRepository,
            DocumentFinancialContributionRepository contributions,
            SecurityAuditService auditService
    ) {
        this.jdbc = jdbc;
        this.monthlyRecordRepository = monthlyRecordRepository;
        this.monthlyRecordService = monthlyRecordService;
        this.obligationRepository = obligationRepository;
        this.allocationRepository = allocationRepository;
        this.contributions = contributions;
        this.auditService = auditService;
    }

    /**
     * Computes deterministic monthly cash receipts/payments derived from eligible posted transactions,
     * aggregates open obligations, evaluates completeness, and compares against existing monthly record.
     */
    @Transactional(readOnly = true)
    public LedgerMonthlyProjectionResponse getMonthlyProjection(BusinessAccessContext context, String yearMonth) {
        UUID businessId = context.businessId();
        YearMonth ym = parseYearMonth(yearMonth);
        LocalDate startDate = ym.atDay(1);
        LocalDate endDate = ym.atEndOfMonth();

        // 1. Query posted transactions for the month, strictly excluding INTERNAL_TRANSFER,
        // reversals, and reversed transactions
        String txSql = """
                SELECT event_type, base_amount_pkr
                FROM financial_transactions
                WHERE business_id = ?
                  AND effective_date >= ?
                  AND effective_date <= ?
                  AND event_type != 'INTERNAL_TRANSFER'
                  AND reversed_transaction_id IS NULL
                  AND id NOT IN (
                      SELECT reversed_transaction_id
                      FROM financial_transactions
                      WHERE business_id = ? AND reversed_transaction_id IS NOT NULL
                  )
                """;

        List<Map<String, Object>> txRows = jdbc.queryForList(txSql, businessId, startDate, endDate, businessId);

        BigDecimal directSaleReceipts = BigDecimal.ZERO.setScale(4, RoundingMode.UNNECESSARY);
        BigDecimal receivableCollections = BigDecimal.ZERO.setScale(4, RoundingMode.UNNECESSARY);
        BigDecimal operatingDisbursements = BigDecimal.ZERO.setScale(4, RoundingMode.UNNECESSARY);
        BigDecimal payableSettlements = BigDecimal.ZERO.setScale(4, RoundingMode.UNNECESSARY);

        for (Map<String, Object> row : txRows) {
            String eventType = (String) row.get("event_type");
            BigDecimal amount = (BigDecimal) row.get("base_amount_pkr");
            if (amount == null) continue;

            switch (eventType) {
                case "DIRECT_SALE_RECEIPT" -> directSaleReceipts = directSaleReceipts.add(amount);
                case "RECEIVABLE_COLLECTION" -> receivableCollections = receivableCollections.add(amount);
                case "OPERATING_EXPENSE_PAYMENT" -> operatingDisbursements = operatingDisbursements.add(amount);
                case "PAYABLE_SETTLEMENT" -> payableSettlements = payableSettlements.add(amount);
            }
        }

        BigDecimal cashInflow = directSaleReceipts.add(receivableCollections);
        BigDecimal cashOutflow = operatingDisbursements.add(payableSettlements);
        BigDecimal netCashFlow = cashInflow.subtract(cashOutflow);

        BigDecimal operatingRevenue = directSaleReceipts;
        BigDecimal operatingExpenses = operatingDisbursements;

        // 2. Query open obligations as of month end with historical as-of correctness
        BigDecimal openReceivables = BigDecimal.ZERO.setScale(4, RoundingMode.UNNECESSARY);
        BigDecimal openPayables = BigDecimal.ZERO.setScale(4, RoundingMode.UNNECESSARY);
        boolean hasForeignObligations = false;

        List<FinancialObligation> obligations = obligationRepository.findByBusinessId(businessId);
        for (FinancialObligation ob : obligations) {
            if (ob.getStatus() == ObligationStatus.VOIDED) continue;
            if (ob.getIssueDate() != null && ob.getIssueDate().isAfter(endDate)) continue;

            // Only sum allocations effective on or before month end
            BigDecimal allocated = allocationRepository.sumEffectiveAllocationsAsOf(businessId, ob.getId(), endDate);
            if (allocated == null) allocated = BigDecimal.ZERO;
            BigDecimal outstanding = ob.getOriginalAmount().subtract(allocated).max(BigDecimal.ZERO);

            String ccy = ob.getCurrency() != null ? ob.getCurrency().trim().toUpperCase(Locale.ROOT) : "PKR";
            if ("PKR".equals(ccy)) {
                if (ob.getDirection() == ObligationDirection.RECEIVABLE) {
                    openReceivables = openReceivables.add(outstanding);
                } else if (ob.getDirection() == ObligationDirection.PAYABLE) {
                    openPayables = openPayables.add(outstanding);
                }
            } else {
                hasForeignObligations = true;
            }
        }

        // 3. Query current cash balance EOM (accounts opening balance + movements in base currency PKR)
        BigDecimal currentCashBalance = queryTotalCashBalanceAsOf(businessId, endDate);

        // Check for active accounts and currency isolation
        String nonPkrSql = "SELECT COUNT(*) FROM financial_accounts WHERE business_id = ? AND status = 'ACTIVE' AND currency != 'PKR'";
        Integer nonPkrCount = jdbc.queryForObject(nonPkrSql, Integer.class, businessId);
        boolean hasForeignAccounts = nonPkrCount != null && nonPkrCount > 0;

        // 4. Query existing monthly record
        Optional<MonthlyRecord> existingRecordOpt = monthlyRecordRepository.findByUserIdAndMonth(businessId, yearMonth);
        MonthlyRecordResponse existingRecord = existingRecordOpt.map(MonthlyRecordResponse::fromEntity).orElse(null);

        // 5. Build Completeness Report
        boolean hasDirectSales = directSaleReceipts.compareTo(BigDecimal.ZERO) > 0;
        boolean hasCollections = receivableCollections.compareTo(BigDecimal.ZERO) > 0;
        boolean hasOperatingOut = operatingDisbursements.compareTo(BigDecimal.ZERO) > 0;
        boolean hasSettlements = payableSettlements.compareTo(BigDecimal.ZERO) > 0;
        boolean hasOpenAr = openReceivables.compareTo(BigDecimal.ZERO) > 0;
        boolean hasOpenAp = openPayables.compareTo(BigDecimal.ZERO) > 0;

        List<String> dataGaps = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        dataGaps.add("COGS is not derived from cash movements; manual inventory valuation or COGS input is recommended.");
        dataGaps.add("Financing obligations and interest expenses are not tracked in ledger cash accounts.");

        if (hasForeignAccounts) {
            warnings.add("Foreign currency accounts detected (e.g. USD, JPY). Cash balance reflects base PKR accounts; foreign balances remain isolated.");
        }
        if (hasForeignObligations) {
            warnings.add("Foreign currency obligations detected and excluded from base PKR AR/AP to preserve currency isolation.");
        }

        if (cashInflow.compareTo(BigDecimal.ZERO) == 0 && cashOutflow.compareTo(BigDecimal.ZERO) == 0) {
            warnings.add("No eligible cash transactions recorded in ledger for " + yearMonth + ".");
        }

        String readinessStatus;
        if (cashInflow.compareTo(BigDecimal.ZERO) > 0 || cashOutflow.compareTo(BigDecimal.ZERO) > 0) {
            readinessStatus = "READY_FOR_PREVIEW";
        } else {
            readinessStatus = "NO_POSTED_TRANSACTIONS";
        }

        LedgerCompletenessReport completeness = new LedgerCompletenessReport(
                hasDirectSales,
                hasCollections,
                hasOperatingOut,
                hasSettlements,
                hasOpenAr,
                hasOpenAp,
                false, // manual cogs
                false, // manual tax
                false, // manual financing
                readinessStatus,
                dataGaps,
                warnings
        );

        return new LedgerMonthlyProjectionResponse(
                businessId,
                yearMonth,
                cashInflow,
                cashOutflow,
                netCashFlow,
                operatingRevenue,
                operatingExpenses,
                openReceivables,
                openPayables,
                currentCashBalance,
                existingRecord != null,
                existingRecord,
                completeness,
                CALCULATION_VERSION,
                "Derived from posted financial transactions excluding internal transfers and reversals."
        );
    }

    /**
     * Authoritative cutover: translates ledger derived figures into a persistent MonthlyRecord,
     * triggering automatic health-score-v1 recalculation while preserving audit history.
     */
    @Transactional
    public LedgerCutoverResponse applyLedgerProjection(
            BusinessAccessContext context,
            LedgerCutoverRequest request,
            HttpServletRequest httpRequest
    ) {
        UUID businessId = context.businessId();
        String yearMonth = request.yearMonth();
        monthlyRecordService.lockBusiness(businessId);

        LedgerMonthlyProjectionResponse projection = getMonthlyProjection(context, yearMonth);

        BigDecimal cashInflow = projection.ledgerCashInflow().setScale(2, RoundingMode.HALF_UP);
        BigDecimal cashOutflow = projection.ledgerCashOutflow().setScale(2, RoundingMode.HALF_UP);
        BigDecimal revenue = projection.ledgerOperatingRevenue().setScale(2, RoundingMode.HALF_UP);
        BigDecimal operatingExpenses = projection.ledgerOperatingExpenses().setScale(2, RoundingMode.HALF_UP);
        BigDecimal cashBalanceEom = projection.currentCashBalanceEom().setScale(2, RoundingMode.HALF_UP);

        // Adjust against confirmed document contribution floors to prevent conflict
        var floors = contributions.floors(businessId, yearMonth);
        if (floors != null) {
            BigDecimal revFloor = floors.get("revenue");
            if (revFloor != null && revenue.compareTo(revFloor) < 0) revenue = revFloor;

            BigDecimal opexFloor = floors.get("operatingExpenses");
            if (opexFloor != null && operatingExpenses.compareTo(opexFloor) < 0) operatingExpenses = opexFloor;

            BigDecimal inFloor = floors.get("cashInflow");
            if (inFloor != null && cashInflow.compareTo(inFloor) < 0) cashInflow = inFloor;

            BigDecimal outFloor = floors.get("cashOutflow");
            if (outFloor != null && cashOutflow.compareTo(outFloor) < 0) cashOutflow = outFloor;
        }

        MonthlyRecord record = new MonthlyRecord();
        record.setUserId(businessId);
        record.setMonth(yearMonth);
        record.setCashInflow(cashInflow);
        record.setCashOutflow(cashOutflow);
        record.setRevenue(revenue);
        record.setOperatingExpenses(operatingExpenses);
        record.setCashBalanceEom(cashBalanceEom);

        record.setReceivablesOutstanding(projection.asOfReceivablesOutstanding().setScale(2, RoundingMode.HALF_UP));
        record.setPayablesOutstanding(projection.asOfPayablesOutstanding().setScale(2, RoundingMode.HALF_UP));

        // Preserve existing manual figures if not explicitly provided in the cutover request
        Optional<MonthlyRecord> existingOpt = monthlyRecordRepository.findByUserIdAndMonth(businessId, yearMonth);
        MonthlyRecord existing = existingOpt.orElse(null);

        if (request.manualCogs() != null) {
            record.setCogs(request.manualCogs().setScale(2, RoundingMode.HALF_UP));
        } else if (existing != null && existing.getCogs() != null) {
            record.setCogs(existing.getCogs());
        }

        if (request.manualInventoryValue() != null) {
            record.setInventoryValue(request.manualInventoryValue().setScale(2, RoundingMode.HALF_UP));
        } else if (existing != null && existing.getInventoryValue() != null) {
            record.setInventoryValue(existing.getInventoryValue());
        }

        if (request.manualLoanOutstanding() != null) {
            record.setLoanOutstanding(request.manualLoanOutstanding().setScale(2, RoundingMode.HALF_UP));
        } else if (existing != null && existing.getLoanOutstanding() != null) {
            record.setLoanOutstanding(existing.getLoanOutstanding());
        }

        if (request.manualInterestExpense() != null) {
            record.setInterestExpense(request.manualInterestExpense().setScale(2, RoundingMode.HALF_UP));
        } else if (existing != null && existing.getInterestExpense() != null) {
            record.setInterestExpense(existing.getInterestExpense());
        }

        String financingType;
        if (request.financingType() != null && !request.financingType().isBlank()) {
            financingType = request.financingType().trim().toLowerCase(Locale.ROOT);
        } else if (existing != null && existing.getFinancingType() != null) {
            financingType = existing.getFinancingType();
        } else {
            financingType = "none";
        }
        record.setFinancingType(financingType);

        MonthlyRecord saved = monthlyRecordService.saveMonthlyRecord(record, context.userId(), request.expectedVersion());

        // Durable Security Audit Log
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("yearMonth", yearMonth);
        metadata.put("ledgerCashInflow", cashInflow.toPlainString());
        metadata.put("ledgerCashOutflow", cashOutflow.toPlainString());
        metadata.put("ledgerRevenue", revenue.toPlainString());
        metadata.put("ledgerOperatingExpenses", operatingExpenses.toPlainString());
        metadata.put("provenance", BRIDGE_PROVENANCE);
        if (request.confirmationNote() != null) {
            metadata.put("confirmationNote", request.confirmationNote());
        }

        auditService.logSuccess(
                AuditEventType.LEDGER_HEALTH_CUTOVER,
                context.userId(),
                null,
                businessId,
                "monthly_record",
                saved.getId().toString(),
                metadata
        );

        MonthlyRecordResponse res = MonthlyRecordResponse.fromEntity(saved);

        return new LedgerCutoverResponse(
                saved.getId(),
                businessId,
                yearMonth,
                saved.getVersion(),
                res,
                BRIDGE_PROVENANCE,
                Instant.now(),
                "Successfully cut over ledger financial transactions into monthly health record for " + yearMonth + "."
        );
    }

    private BigDecimal queryTotalCashBalanceAsOf(UUID businessId, LocalDate asOfDate) {
        String openingSql = """
                SELECT COALESCE(SUM(opening_balance), 0)
                FROM financial_accounts
                WHERE business_id = ?
                  AND status = 'ACTIVE'
                  AND currency = 'PKR'
                  AND opening_date <= ?
                """;
        BigDecimal opening = jdbc.queryForObject(openingSql, BigDecimal.class, businessId, asOfDate);
        if (opening == null) opening = BigDecimal.ZERO;

        String movementSql = """
                SELECT COALESCE(SUM(
                    CASE
                        WHEN m.movement_direction = 'INCREASE' THEN m.amount
                        WHEN m.movement_direction = 'DECREASE' THEN -m.amount
                        ELSE 0
                    END
                ), 0)
                FROM account_movements m
                JOIN financial_accounts a ON m.account_id = a.id
                WHERE m.business_id = ?
                  AND a.currency = 'PKR'
                  AND a.opening_date <= ?
                  AND m.effective_date <= ?
                """;
        BigDecimal netMovements = jdbc.queryForObject(movementSql, BigDecimal.class, businessId, asOfDate, asOfDate);
        if (netMovements == null) netMovements = BigDecimal.ZERO;

        return opening.add(netMovements).setScale(4, RoundingMode.HALF_UP);
    }

    private YearMonth parseYearMonth(String ym) {
        if (ym == null || !ym.matches("^[0-9]{4}-(0[1-9]|1[0-2])$")) {
            throw new IllegalArgumentException("yearMonth must follow YYYY-MM format");
        }
        try {
            return YearMonth.parse(ym);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid yearMonth: " + ym, e);
        }
    }
}
