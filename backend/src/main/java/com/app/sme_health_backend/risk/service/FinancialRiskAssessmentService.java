package com.app.sme_health_backend.risk.service;

import com.app.sme_health_backend.accounting.draft.DraftStatus;
import com.app.sme_health_backend.accounting.draft.RecordDraft;
import com.app.sme_health_backend.accounting.draft.RecordDraftRepository;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.model.MovementDirection;
import com.app.sme_health_backend.accounting.model.TransactionEventType;
import com.app.sme_health_backend.accounting.repository.AccountMovementRepository;
import com.app.sme_health_backend.accounting.repository.AccountMovementSum;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.counterparty.Counterparty;
import com.app.sme_health_backend.counterparty.CounterpartyRepository;
import com.app.sme_health_backend.counterparty.CounterpartyStatus;
import com.app.sme_health_backend.forecast.dto.DailyCashForecastResponse;
import com.app.sme_health_backend.forecast.dto.DailyForecastRow;
import com.app.sme_health_backend.forecast.dto.ForecastItemDetail;
import com.app.sme_health_backend.forecast.service.DailyCashForecastService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.obligation.*;
import com.app.sme_health_backend.obligation.aging.*;
import com.app.sme_health_backend.reconciliation.model.StatementEntryStatus;
import com.app.sme_health_backend.reconciliation.repository.BankStatementEntryRepository;
import com.app.sme_health_backend.recurring.calculator.RecurrenceCalculator;
import com.app.sme_health_backend.recurring.entity.RecurringRule;
import com.app.sme_health_backend.recurring.entity.RecurringRuleOccurrence;
import com.app.sme_health_backend.recurring.model.RecurringOccurrenceStatus;
import com.app.sme_health_backend.recurring.model.RecurringRuleStatus;
import com.app.sme_health_backend.recurring.repository.RecurringRuleOccurrenceRepository;
import com.app.sme_health_backend.recurring.repository.RecurringRuleRepository;
import com.app.sme_health_backend.risk.dto.*;
import com.app.sme_health_backend.risk.exception.RiskValidationException;
import com.app.sme_health_backend.risk.model.*;
import com.app.sme_health_backend.risk.policy.RiskThresholdPolicy;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class FinancialRiskAssessmentService {

    private static final String VERSION = "1.0";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Karachi");

    private final DailyCashForecastService forecastService;
    private final FinancialAccountRepository accountRepository;
    private final AccountMovementRepository movementRepository;
    private final FinancialObligationService obligationService;
    private final FinancialObligationRepository obligationRepository;
    private final ObligationPaymentScheduleRepository scheduleRepository;
    private final ObligationPaymentAllocationRepository allocationRepository;
    private final CounterpartyRepository counterpartyRepository;
    private final RecurringRuleRepository recurringRuleRepository;
    private final RecurringRuleOccurrenceRepository recurringOccurrenceRepository;
    private final RecordDraftRepository recordDraftRepository;
    private final BankStatementEntryRepository statementEntryRepository;
    private final BusinessAuthorizationService authorizationService;

    public FinancialRiskAssessmentService(
            DailyCashForecastService forecastService,
            FinancialAccountRepository accountRepository,
            AccountMovementRepository movementRepository,
            FinancialObligationService obligationService,
            FinancialObligationRepository obligationRepository,
            ObligationPaymentScheduleRepository scheduleRepository,
            ObligationPaymentAllocationRepository allocationRepository,
            CounterpartyRepository counterpartyRepository,
            RecurringRuleRepository recurringRuleRepository,
            RecurringRuleOccurrenceRepository recurringOccurrenceRepository,
            RecordDraftRepository recordDraftRepository,
            BankStatementEntryRepository statementEntryRepository,
            BusinessAuthorizationService authorizationService
    ) {
        this.forecastService = forecastService;
        this.accountRepository = accountRepository;
        this.movementRepository = movementRepository;
        this.obligationService = obligationService;
        this.obligationRepository = obligationRepository;
        this.scheduleRepository = scheduleRepository;
        this.allocationRepository = allocationRepository;
        this.counterpartyRepository = counterpartyRepository;
        this.recurringRuleRepository = recurringRuleRepository;
        this.recurringOccurrenceRepository = recurringOccurrenceRepository;
        this.recordDraftRepository = recordDraftRepository;
        this.statementEntryRepository = statementEntryRepository;
        this.authorizationService = authorizationService;
    }

    public RiskAssessmentResponse getAssessment(
            BusinessAccessContext context,
            String asOfDateStr,
            Integer horizonDaysParam,
            String currencyParam,
            String categoryParam,
            String severityParam
    ) {
        requirePermission(context);

        LocalDate asOfDate = parseAsOfDate(asOfDateStr);
        int horizonDays = sanitizeHorizonDays(horizonDaysParam);
        String currency = sanitizeCurrency(currencyParam);
        RiskCategory categoryFilter = parseCategoryFilter(categoryParam);
        RiskSeverity severityFilter = parseSeverityFilter(severityParam);

        return assessRisks(context, asOfDate, horizonDays, currency, categoryFilter, severityFilter);
    }

    public List<FinancialRiskInsight> getInsights(
            BusinessAccessContext context,
            String asOfDateStr,
            Integer horizonDaysParam,
            String currencyParam,
            String categoryParam,
            String severityParam
    ) {
        return getAssessment(context, asOfDateStr, horizonDaysParam, currencyParam, categoryParam, severityParam).insights();
    }

    public RiskSummary getSummary(
            BusinessAccessContext context,
            String asOfDateStr,
            Integer horizonDaysParam,
            String currencyParam
    ) {
        return getAssessment(context, asOfDateStr, horizonDaysParam, currencyParam, null, null).summary();
    }

    private RiskAssessmentResponse assessRisks(
            BusinessAccessContext context,
            LocalDate asOfDate,
            int horizonDays,
            String currency,
            RiskCategory categoryFilter,
            RiskSeverity severityFilter
    ) {
        UUID businessId = context.businessId();
        Map<String, FinancialRiskInsight> uniqueInsights = new LinkedHashMap<>();
        List<RiskCategoryAssessment> categoryAssessments = new ArrayList<>();

        // 1. Calculate authoritative account cash balances as of asOfDate
        Map<UUID, BigDecimal> accountBalances = computeAccountBalances(businessId, asOfDate, currency);
        BigDecimal availableCash = accountBalances.values().stream()
                .filter(b -> b.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // R01 — Projected Cash Deficit
        evaluateProjectedCashDeficit(context, businessId, currency, asOfDate, horizonDays, uniqueInsights, categoryAssessments);

        // R02 — Upcoming Payment Pressure
        evaluateUpcomingPaymentPressure(businessId, currency, asOfDate, availableCash, uniqueInsights, categoryAssessments);

        // R03 & R04 — Overdue Receivables and Customer Concentration
        evaluateReceivablesAndConcentration(context, businessId, currency, asOfDate, uniqueInsights, categoryAssessments);

        // R05 — Recurring Operating Cash Pressure
        evaluateRecurringCashPressure(businessId, currency, asOfDate, horizonDays, availableCash, uniqueInsights, categoryAssessments);

        // R06 — Negative Actual Cash Position
        evaluateNegativeActualCash(businessId, currency, accountBalances, uniqueInsights, categoryAssessments);

        // R07 — Financial Data Completeness Risk
        evaluateDataCompleteness(businessId, uniqueInsights, categoryAssessments);

        // R08 — Bank Reconciliation Coverage
        evaluateBankReconciliation(businessId, currency, uniqueInsights, categoryAssessments);

        // Apply filters & deterministic sorting
        List<FinancialRiskInsight> sortedInsights = uniqueInsights.values().stream()
                .filter(i -> categoryFilter == null || i.category() == categoryFilter)
                .filter(i -> severityFilter == null || i.severity() == severityFilter)
                .sorted(Comparator
                        .comparing((FinancialRiskInsight i) -> severityRank(i.severity()))
                        .thenComparing(i -> i.category().ordinal())
                        .thenComparing(FinancialRiskInsight::riskCode)
                        .thenComparing(FinancialRiskInsight::id))
                .collect(Collectors.toList());

        // Compute summary counts across ALL unfiltered insights
        int criticalCount = 0;
        int highCount = 0;
        int mediumCount = 0;
        int lowCount = 0;
        int notAssessedCount = 0;
        int assessedCount = 0;

        for (RiskCategoryAssessment ca : categoryAssessments) {
            if ("NOT_ASSESSED".equals(ca.status())) {
                notAssessedCount++;
            } else {
                assessedCount++;
            }
        }

        for (FinancialRiskInsight in : uniqueInsights.values()) {
            if (in.severity() == RiskSeverity.CRITICAL) criticalCount++;
            else if (in.severity() == RiskSeverity.HIGH) highCount++;
            else if (in.severity() == RiskSeverity.MEDIUM) mediumCount++;
            else if (in.severity() == RiskSeverity.LOW) lowCount++;
        }

        RiskSeverity highestSeverity = RiskSeverity.LOW;
        if (criticalCount > 0) highestSeverity = RiskSeverity.CRITICAL;
        else if (highCount > 0) highestSeverity = RiskSeverity.HIGH;
        else if (mediumCount > 0) highestSeverity = RiskSeverity.MEDIUM;
        else if (uniqueInsights.isEmpty() && notAssessedCount > 0) highestSeverity = RiskSeverity.NOT_ASSESSED;

        boolean hasCompletenessIssues = uniqueInsights.values().stream()
                .anyMatch(i -> i.category() == RiskCategory.DATA_INTEGRITY || i.completeness() == RiskCompleteness.PARTIAL || i.completeness() == RiskCompleteness.INCOMPLETE);

        RiskSummary summary = new RiskSummary(
                criticalCount,
                highCount,
                mediumCount,
                lowCount,
                notAssessedCount,
                assessedCount,
                RiskCategory.values().length,
                asOfDate,
                horizonDays,
                currency,
                highestSeverity,
                hasCompletenessIssues
        );

        return new RiskAssessmentResponse(
                VERSION,
                businessId,
                asOfDate,
                horizonDays,
                currency,
                summary,
                categoryAssessments,
                sortedInsights,
                Instant.now()
        );
    }

    // =========================================================================
    // R01 — PROJECTED CASH DEFICIT
    // =========================================================================
    private void evaluateProjectedCashDeficit(
            BusinessAccessContext context,
            UUID businessId,
            String currency,
            LocalDate asOfDate,
            int horizonDays,
            Map<String, FinancialRiskInsight> uniqueInsights,
            List<RiskCategoryAssessment> categoryAssessments
    ) {
        DailyCashForecastResponse forecast = forecastService.getDailyCashForecast(context, currency, horizonDays, asOfDate.toString());

        LocalDate firstDeficitDate = null;
        BigDecimal minBalance = BigDecimal.ZERO;
        DailyForecastRow minRow = null;
        DailyForecastRow firstDeficitRow = null;

        if (forecast != null && forecast.dailyRows() != null) {
            for (DailyForecastRow row : forecast.dailyRows()) {
                BigDecimal closing = new BigDecimal(row.projectedClosingCash());
                if (row.isDeficit() || closing.compareTo(BigDecimal.ZERO) < 0) {
                    if (firstDeficitDate == null) {
                        firstDeficitDate = row.date();
                        firstDeficitRow = row;
                    }
                    if (minRow == null || closing.compareTo(minBalance) < 0) {
                        minBalance = closing;
                        minRow = row;
                    }
                }
            }
        }

        if (firstDeficitDate != null && minRow != null) {
            BigDecimal maxShortfall = minBalance.abs().setScale(2, RoundingMode.HALF_UP);
            String insightId = "RISK-R01-DEFICIT-" + currency + "-" + firstDeficitDate;

            List<RiskEvidenceReference> evidence = new ArrayList<>();
            evidence.add(new RiskEvidenceReference(
                    EvidenceType.DAILY_FORECAST,
                    firstDeficitDate.toString(),
                    "Projected balance first turns negative at " + currency + " " + firstDeficitRow.projectedClosingCash() + " on " + firstDeficitDate,
                    firstDeficitRow.projectedClosingCash(),
                    firstDeficitDate,
                    null,
                    Map.of("projectedBalance", firstDeficitRow.projectedClosingCash(), "expectedInflows", firstDeficitRow.totalExpectedInflows(), "expectedOutflows", firstDeficitRow.totalExpectedOutflows())
            ));

            if (!firstDeficitDate.equals(minRow.date())) {
                evidence.add(new RiskEvidenceReference(
                        EvidenceType.DAILY_FORECAST,
                        minRow.date().toString(),
                        "Projected cash reaches lowest trough of " + currency + " " + minRow.projectedClosingCash() + " on " + minRow.date(),
                        minRow.projectedClosingCash(),
                        minRow.date(),
                        null,
                        Map.of("projectedBalance", minRow.projectedClosingCash())
                ));
            }

            // Include top contributing outflows up to the deficit date
            if (forecast.dailyRows() != null) {
                for (DailyForecastRow row : forecast.dailyRows()) {
                    if (!row.date().isAfter(firstDeficitDate) && row.contributingItems() != null) {
                        for (ForecastItemDetail item : row.contributingItems()) {
                            if ("PAYABLE".equalsIgnoreCase(item.direction()) || "EXPENSE".equalsIgnoreCase(item.direction())) {
                                evidence.add(new RiskEvidenceReference(
                                        EvidenceType.FINANCIAL_OBLIGATION,
                                        item.sourceId(),
                                        "Expected outflow: " + item.description() + " (" + item.counterpartyName() + ")",
                                        item.amount(),
                                        item.forecastDate(),
                                        item.counterpartyName(),
                                        Map.of("referenceNumber", item.referenceNumber() != null ? item.referenceNumber() : "")
                                ));
                                if (evidence.size() >= 5) break;
                            }
                        }
                    }
                    if (evidence.size() >= 5) break;
                }
            }

            FinancialRiskInsight insight = new FinancialRiskInsight(
                    insightId,
                    "R01_PROJECTED_CASH_DEFICIT",
                    RiskCategory.LIQUIDITY_FORECAST,
                    RiskSeverity.CRITICAL,
                    "Projected Cash Shortfall of " + currency + " " + scale(maxShortfall) + " by " + firstDeficitDate,
                    "Authoritative daily forecasting projects your cash balance will fall below zero to " + currency + " " + scale(minBalance) + " on " + minRow.date() + ", with an initial deficit appearing on " + firstDeficitDate + ".",
                    "Without timely collections or delayed disbursements, the business will face a cash liquidity shortfall, potentially causing delayed payments, operational disruption, or penalties.",
                    currency,
                    maxShortfall,
                    null,
                    "By " + firstDeficitDate,
                    firstDeficitDate,
                    minRow.date(),
                    evidence,
                    new RiskActionRecommendation(
                            "EXPEDITE_COLLECTIONS_AND_DEFER_PAYABLES",
                            "Review payment schedule and expedite customer collections",
                            "Examine upcoming supplier disbursements before " + firstDeficitDate + " to arrange payment extensions and prioritize collection of outstanding receivables.",
                            Urgency.IMMEDIATE,
                            "/records?view=forecast"
                    ),
                    RiskCompleteness.COMPLETE
            );

            uniqueInsights.put(insightId, insight);
            categoryAssessments.add(new RiskCategoryAssessment(
                    RiskCategory.LIQUIDITY_FORECAST,
                    RiskCategory.LIQUIDITY_FORECAST.getDisplayName(),
                    "ASSESSED",
                    1,
                    RiskSeverity.CRITICAL,
                    null
            ));
        } else {
            categoryAssessments.add(new RiskCategoryAssessment(
                    RiskCategory.LIQUIDITY_FORECAST,
                    RiskCategory.LIQUIDITY_FORECAST.getDisplayName(),
                    "ASSESSED",
                    0,
                    RiskSeverity.LOW,
                    "No projected cash deficit within the " + horizonDays + "-day forecast window."
            ));
        }
    }

    // =========================================================================
    // R02 — UPCOMING PAYMENT PRESSURE
    // =========================================================================
    private void evaluateUpcomingPaymentPressure(
            UUID businessId,
            String currency,
            LocalDate asOfDate,
            BigDecimal availableCash,
            Map<String, FinancialRiskInsight> uniqueInsights,
            List<RiskCategoryAssessment> categoryAssessments
    ) {
        LocalDate date7 = asOfDate.plusDays(7);
        LocalDate date30 = asOfDate.plusDays(30);

        List<FinancialObligation> openPayables = obligationRepository.findByBusinessIdAndDirection(businessId, ObligationDirection.PAYABLE).stream()
                .filter(o -> o.getStatus() == ObligationStatus.OPEN && currency.equalsIgnoreCase(o.getCurrency()))
                .toList();

        List<FinancialObligation> openReceivables = obligationRepository.findByBusinessIdAndDirection(businessId, ObligationDirection.RECEIVABLE).stream()
                .filter(o -> o.getStatus() == ObligationStatus.OPEN && currency.equalsIgnoreCase(o.getCurrency()))
                .toList();

        // Calculate paid amounts per obligation from confirmed allocations
        Map<UUID, BigDecimal> paidByObligation = new HashMap<>();
        for (ObligationPaymentAllocation alloc : allocationRepository.findByBusinessId(businessId)) {
            if (alloc.getStatus() == AllocationStatus.EFFECTIVE && alloc.getAmount() != null) {
                paidByObligation.merge(alloc.getObligationId(), alloc.getAmount(), BigDecimal::add);
            }
        }

        // Query planned schedules
        List<ObligationPaymentSchedule> allSchedules = scheduleRepository.findByBusinessId(businessId).stream()
                .filter(s -> s.getStatus() == ScheduleStatus.PLANNED)
                .toList();
        Map<UUID, List<ObligationPaymentSchedule>> schedulesByOb = allSchedules.stream()
                .collect(Collectors.groupingBy(ObligationPaymentSchedule::getObligationId));

        BigDecimal payables7 = BigDecimal.ZERO;
        BigDecimal payables30 = BigDecimal.ZERO;
        List<RiskEvidenceReference> evidence7 = new ArrayList<>();

        for (FinancialObligation ob : openPayables) {
            BigDecimal paid = paidByObligation.getOrDefault(ob.getId(), BigDecimal.ZERO);
            BigDecimal outstanding = ob.getOriginalAmount().subtract(paid);
            if (outstanding.compareTo(BigDecimal.ZERO) <= 0) continue;

            List<ObligationPaymentSchedule> schedules = schedulesByOb.getOrDefault(ob.getId(), Collections.emptyList());
            if (!schedules.isEmpty()) {
                for (ObligationPaymentSchedule s : schedules) {
                    BigDecimal exp = s.getExpectedAmount();
                    if (exp == null || exp.compareTo(BigDecimal.ZERO) <= 0) continue;
                    if (!s.getExpectedDate().isBefore(asOfDate) && !s.getExpectedDate().isAfter(date7)) {
                        payables7 = payables7.add(exp);
                        if (evidence7.size() < 5) {
                            evidence7.add(new RiskEvidenceReference(
                                    EvidenceType.FINANCIAL_OBLIGATION,
                                    s.getId().toString(),
                                    "Scheduled payable installment due " + s.getExpectedDate() + " (" + (ob.getReferenceNumber() != null ? ob.getReferenceNumber() : ob.getId()) + ")",
                                    scale(exp),
                                    s.getExpectedDate(),
                                    null,
                                    Map.of("obligationId", ob.getId().toString())
                            ));
                        }
                    }
                    if (!s.getExpectedDate().isBefore(asOfDate) && !s.getExpectedDate().isAfter(date30)) {
                        payables30 = payables30.add(exp);
                    }
                }
            } else {
                if (ob.getDueDate() != null) {
                    if (!ob.getDueDate().isBefore(asOfDate) && !ob.getDueDate().isAfter(date7)) {
                        payables7 = payables7.add(outstanding);
                        if (evidence7.size() < 5) {
                            evidence7.add(new RiskEvidenceReference(
                                    EvidenceType.FINANCIAL_OBLIGATION,
                                    ob.getId().toString(),
                                    "Payable obligation due " + ob.getDueDate() + " (" + (ob.getReferenceNumber() != null ? ob.getReferenceNumber() : ob.getId()) + ")",
                                    scale(outstanding),
                                    ob.getDueDate(),
                                    null,
                                    Map.of("originalAmount", ob.getOriginalAmount())
                            ));
                        }
                    }
                    if (!ob.getDueDate().isBefore(asOfDate) && !ob.getDueDate().isAfter(date30)) {
                        payables30 = payables30.add(outstanding);
                    }
                }
            }
        }

        // Expected customer collections
        BigDecimal receivables7 = BigDecimal.ZERO;
        for (FinancialObligation ob : openReceivables) {
            BigDecimal collected = paidByObligation.getOrDefault(ob.getId(), BigDecimal.ZERO);
            BigDecimal outstanding = ob.getOriginalAmount().subtract(collected);
            if (outstanding.compareTo(BigDecimal.ZERO) <= 0) continue;

            List<ObligationPaymentSchedule> schedules = schedulesByOb.getOrDefault(ob.getId(), Collections.emptyList());
            if (!schedules.isEmpty()) {
                for (ObligationPaymentSchedule s : schedules) {
                    if (!s.getExpectedDate().isBefore(asOfDate) && !s.getExpectedDate().isAfter(date7)) {
                        receivables7 = receivables7.add(s.getExpectedAmount());
                    }
                }
            } else if (ob.getDueDate() != null && !ob.getDueDate().isBefore(asOfDate) && !ob.getDueDate().isAfter(date7)) {
                receivables7 = receivables7.add(outstanding);
            }
        }

        int insightCount = 0;
        RiskSeverity categoryHighest = RiskSeverity.LOW;

        // Check 7-day payment pressure
        if (payables7.compareTo(BigDecimal.ZERO) > 0 && availableCash.compareTo(payables7) < 0) {
            BigDecimal gap7 = payables7.subtract(availableCash.add(receivables7));
            RiskSeverity severity = gap7.compareTo(BigDecimal.ZERO) > 0 ? RiskSeverity.CRITICAL : RiskSeverity.HIGH;
            BigDecimal reportedGap = gap7.compareTo(BigDecimal.ZERO) > 0 ? gap7 : payables7.subtract(availableCash);

            String insightId = "RISK-R02-PAYMENT-PRESSURE-7D";
            FinancialRiskInsight insight = new FinancialRiskInsight(
                    insightId,
                    "R02_UPCOMING_PAYMENT_PRESSURE",
                    RiskCategory.UPCOMING_PAYMENTS,
                    severity,
                    "Upcoming 7-Day Payment Pressure: " + currency + " " + scale(payables7) + " Due",
                    "Supplier obligations of " + currency + " " + scale(payables7) + " are due within the next 7 days, against current cash of " + currency + " " + scale(availableCash) + " (coverage gap: " + currency + " " + scale(reportedGap) + ").",
                    "A high concentration of payments due in the immediate 7-day window without sufficient confirmed liquidity exposes the business to payment default risk.",
                    currency,
                    scaleAmount(reportedGap),
                    null,
                    "Next 7 days (" + asOfDate + " to " + date7 + ")",
                    asOfDate,
                    date7,
                    evidence7,
                    new RiskActionRecommendation(
                            "NEGOTIATE_SUPPLIER_EXTENSION",
                            "Prioritize upcoming supplier disbursements and arrange liquidity",
                            "Contact suppliers with payments due in the next 7 days to request staggered payment terms or draw on short-term credit facilities.",
                            Urgency.IMMEDIATE,
                            "/records?view=payables"
                    ),
                    RiskCompleteness.COMPLETE
            );

            uniqueInsights.put(insightId, insight);
            insightCount++;
            categoryHighest = severity;
        } else if (payables30.compareTo(BigDecimal.ZERO) > 0 && availableCash.compareTo(payables30) < 0) {
            BigDecimal gap30 = payables30.subtract(availableCash);
            String insightId = "RISK-R02-PAYMENT-PRESSURE-30D";
            FinancialRiskInsight insight = new FinancialRiskInsight(
                    insightId,
                    "R02_UPCOMING_PAYMENT_PRESSURE",
                    RiskCategory.UPCOMING_PAYMENTS,
                    RiskSeverity.HIGH,
                    "Upcoming 30-Day Payment Pressure: " + currency + " " + scale(payables30) + " Due",
                    "Supplier obligations of " + currency + " " + scale(payables30) + " are due within 30 days, exceeding available cash of " + currency + " " + scale(availableCash) + ".",
                    "Upcoming cash commitments exceed current cash reserves, requiring steady incoming revenue to avoid liquidity deficits.",
                    currency,
                    scaleAmount(gap30),
                    null,
                    "Next 30 days (" + asOfDate + " to " + date30 + ")",
                    asOfDate,
                    date30,
                    evidence7,
                    new RiskActionRecommendation(
                            "ALIGN_COLLECTIONS_AND_DISBURSEMENTS",
                            "Plan cash disbursements against expected revenue",
                            "Review 30-day payment commitments and align supplier release dates with customer collection milestones.",
                            Urgency.SHORT_TERM,
                            "/records?view=payables"
                    ),
                    RiskCompleteness.COMPLETE
            );

            uniqueInsights.put(insightId, insight);
            insightCount++;
            categoryHighest = RiskSeverity.HIGH;
        }

        categoryAssessments.add(new RiskCategoryAssessment(
                RiskCategory.UPCOMING_PAYMENTS,
                RiskCategory.UPCOMING_PAYMENTS.getDisplayName(),
                "ASSESSED",
                insightCount,
                categoryHighest,
                insightCount == 0 ? "Upcoming payments within 7 and 30 days are comfortably covered by cash reserves." : null
        ));
    }

    // =========================================================================
    // R03 & R04 — RECEIVABLES AGING & CUSTOMER CONCENTRATION
    // =========================================================================
    private void evaluateReceivablesAndConcentration(
            BusinessAccessContext context,
            UUID businessId,
            String currency,
            LocalDate asOfDate,
            Map<String, FinancialRiskInsight> uniqueInsights,
            List<RiskCategoryAssessment> categoryAssessments
    ) {
        ObligationAgingResult agingResult = obligationService.getAging(context, asOfDate.toString());

        // Batch counterparty lookup
        Map<UUID, Counterparty> counterpartyMap = counterpartyRepository.findAllByBusinessIdAndStatusOrderByDisplayNameAsc(businessId, CounterpartyStatus.ACTIVE).stream()
                .collect(Collectors.toMap(Counterparty::getId, c -> c));

        // R03 Overdue Receivables Evaluation
        BigDecimal totalReceivables = BigDecimal.ZERO;
        BigDecimal totalOverdue = BigDecimal.ZERO;
        List<ObligationAgingDetail> receivableDetails = new ArrayList<>();

        if (agingResult.receivables() != null) {
            totalReceivables = agingResult.receivables().totalOutstanding();
            totalOverdue = agingResult.receivableOverdueOutstanding();
        }

        if (agingResult.details() != null) {
            for (ObligationAgingDetail d : agingResult.details()) {
                if (d.direction() == ObligationDirection.RECEIVABLE && d.isActive() && d.asOfOutstanding().compareTo(BigDecimal.ZERO) > 0) {
                    receivableDetails.add(d);
                }
            }
        }

        int r03InsightCount = 0;
        RiskSeverity r03Highest = RiskSeverity.LOW;

        if (totalOverdue.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal overdue91 = agingResult.receivables().getBucket(AgingBucket.OVERDUE_91_PLUS).totalOutstanding();
            BigDecimal overdue61 = agingResult.receivables().getBucket(AgingBucket.OVERDUE_61_90).totalOutstanding();
            BigDecimal overdue31 = agingResult.receivables().getBucket(AgingBucket.OVERDUE_31_60).totalOutstanding();

            BigDecimal overdueRatio = totalReceivables.compareTo(BigDecimal.ZERO) > 0
                    ? totalOverdue.divide(totalReceivables, 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal overduePct = overdueRatio.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);

            RiskSeverity severity = RiskSeverity.MEDIUM;
            if (overdue91.compareTo(BigDecimal.ZERO) > 0 || overdueRatio.compareTo(RiskThresholdPolicy.OVERDUE_RATIO_CRITICAL_THRESHOLD) >= 0) {
                severity = RiskSeverity.CRITICAL;
            } else if (overdue61.add(overdue31).compareTo(BigDecimal.ZERO) > 0 || overdueRatio.compareTo(RiskThresholdPolicy.OVERDUE_RATIO_HIGH_THRESHOLD) >= 0) {
                severity = RiskSeverity.HIGH;
            }

            // Top overdue evidence items
            List<RiskEvidenceReference> r03Evidence = new ArrayList<>();
            List<ObligationAgingDetail> sortedOverdue = receivableDetails.stream()
                    .filter(ObligationAgingDetail::isOverdue)
                    .sorted(Comparator.comparing((ObligationAgingDetail d) -> d.daysOverdue() != null ? d.daysOverdue() : 0, Comparator.reverseOrder())
                            .thenComparing(ObligationAgingDetail::asOfOutstanding, Comparator.reverseOrder()))
                    .limit(5)
                    .toList();

            for (ObligationAgingDetail d : sortedOverdue) {
                UUID obId = UUID.fromString(d.obligationId());
                Optional<FinancialObligation> obOpt = obligationRepository.findByIdAndBusinessId(obId, businessId);
                String cpName = "Customer";
                String refNum = d.obligationId();
                if (obOpt.isPresent()) {
                    FinancialObligation ob = obOpt.get();
                    if (ob.getReferenceNumber() != null) refNum = ob.getReferenceNumber();
                    if (ob.getCounterpartyId() != null && counterpartyMap.containsKey(ob.getCounterpartyId())) {
                        cpName = counterpartyMap.get(ob.getCounterpartyId()).getDisplayName();
                    }
                }

                r03Evidence.add(new RiskEvidenceReference(
                        EvidenceType.FINANCIAL_OBLIGATION,
                        d.obligationId(),
                        "Overdue invoice: " + refNum + " (" + d.daysOverdue() + " days overdue)",
                        scale(d.asOfOutstanding()),
                        d.dueDate(),
                        cpName,
                        Map.of("daysOverdue", d.daysOverdue() != null ? d.daysOverdue() : 0, "bucket", d.assignedBucket() != null ? d.assignedBucket().name() : "")
                ));
            }

            String insightId = "RISK-R03-RECEIVABLES-OVERDUE";
            FinancialRiskInsight insight = new FinancialRiskInsight(
                    insightId,
                    "R03_OVERDUE_RECEIVABLES",
                    RiskCategory.RECEIVABLES_COLLECTION,
                    severity,
                    "Overdue Receivables of " + currency + " " + scale(totalOverdue) + " (" + overduePct + "% of total)",
                    "A total of " + currency + " " + scale(totalOverdue) + " in customer receivables is overdue across aging buckets, representing " + overduePct + "% of total outstanding receivables.",
                    "Delayed customer collections tie up working capital, increase carrying costs, and indicate elevated default or non-payment risk.",
                    currency,
                    scaleAmount(totalOverdue),
                    overduePct,
                    "As of " + asOfDate,
                    asOfDate,
                    asOfDate,
                    r03Evidence,
                    new RiskActionRecommendation(
                            "ACCELERATE_OVERDUE_COLLECTIONS",
                            "Prioritize follow-up on overdue customer balances",
                            "Focus immediate collection efforts on invoices overdue by more than 30 days and enforce credit limits on delinquent accounts.",
                            Urgency.IMMEDIATE,
                            "/records?view=receivables"
                    ),
                    RiskCompleteness.COMPLETE
            );

            uniqueInsights.put(insightId, insight);
            r03InsightCount++;
            r03Highest = severity;
        }

        categoryAssessments.add(new RiskCategoryAssessment(
                RiskCategory.RECEIVABLES_COLLECTION,
                RiskCategory.RECEIVABLES_COLLECTION.getDisplayName(),
                "ASSESSED",
                r03InsightCount,
                r03Highest,
                r03InsightCount == 0 ? "No overdue receivables detected." : null
        ));

        // R04 Customer Receivables Concentration Evaluation
        int r04InsightCount = 0;
        RiskSeverity r04Highest = RiskSeverity.LOW;

        if (totalReceivables.compareTo(BigDecimal.ZERO) > 0) {
            // Group by counterparty
            Map<UUID, BigDecimal> byCounterparty = new HashMap<>();
            for (ObligationAgingDetail d : receivableDetails) {
                UUID obId = UUID.fromString(d.obligationId());
                Optional<FinancialObligation> obOpt = obligationRepository.findByIdAndBusinessId(obId, businessId);
                if (obOpt.isPresent() && obOpt.get().getCounterpartyId() != null) {
                    byCounterparty.merge(obOpt.get().getCounterpartyId(), d.asOfOutstanding(), BigDecimal::add);
                }
            }

            Map.Entry<UUID, BigDecimal> maxEntry = byCounterparty.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .orElse(null);

            if (maxEntry != null) {
                BigDecimal largestOutstanding = maxEntry.getValue();
                BigDecimal concRatio = largestOutstanding.divide(totalReceivables, 4, RoundingMode.HALF_UP);
                BigDecimal concPct = concRatio.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);

                if (concPct.compareTo(RiskThresholdPolicy.CUSTOMER_CONCENTRATION_MEDIUM_THRESHOLD) >= 0) {
                    RiskSeverity severity = RiskSeverity.MEDIUM;
                    if (concPct.compareTo(RiskThresholdPolicy.CUSTOMER_CONCENTRATION_CRITICAL_THRESHOLD) >= 0) {
                        severity = RiskSeverity.CRITICAL;
                    } else if (concPct.compareTo(RiskThresholdPolicy.CUSTOMER_CONCENTRATION_HIGH_THRESHOLD) >= 0) {
                        severity = RiskSeverity.HIGH;
                    }

                    Counterparty cp = counterpartyMap.get(maxEntry.getKey());
                    String cpName = cp != null ? cp.getDisplayName() : "Customer " + maxEntry.getKey();

                    List<RiskEvidenceReference> r04Evidence = new ArrayList<>();
                    r04Evidence.add(new RiskEvidenceReference(
                            EvidenceType.COUNTERPARTY,
                            maxEntry.getKey().toString(),
                            "Counterparty " + cpName + " holds " + currency + " " + scale(largestOutstanding) + " of " + currency + " " + scale(totalReceivables) + " total receivables",
                            scale(largestOutstanding),
                            asOfDate,
                            cpName,
                            Map.of("concentrationPercentage", concPct, "totalReceivables", totalReceivables)
                    ));

                    String insightId = "RISK-R04-CONCENTRATION-" + maxEntry.getKey();
                    FinancialRiskInsight insight = new FinancialRiskInsight(
                            insightId,
                            "R04_CUSTOMER_CONCENTRATION",
                            RiskCategory.COUNTERPARTY_CONCENTRATION,
                            severity,
                            "High Customer Concentration: " + cpName + " (" + concPct + "% of receivables)",
                            cpName + " accounts for " + currency + " " + scale(largestOutstanding) + " (" + concPct + "%) of your total outstanding receivables (" + currency + " " + scale(totalReceivables) + ").",
                            "Over-reliance on a single debtor creates severe business vulnerability if that counterparty delays payment, enters distress, or disputes billings.",
                            currency,
                            scaleAmount(largestOutstanding),
                            concPct,
                            "As of " + asOfDate,
                            asOfDate,
                            asOfDate,
                            r04Evidence,
                            new RiskActionRecommendation(
                                    "DIVERSIFY_RECEIVABLES_EXPOSURE",
                                    "Mitigate single-customer credit concentration",
                                    "Consider advance payment milestones, reduced credit windows, or insurance on major customer contracts.",
                                    Urgency.SHORT_TERM,
                                    "/records?view=contacts"
                            ),
                            RiskCompleteness.COMPLETE
                    );

                    uniqueInsights.put(insightId, insight);
                    r04InsightCount++;
                    r04Highest = severity;
                }
            }
        }

        categoryAssessments.add(new RiskCategoryAssessment(
                RiskCategory.COUNTERPARTY_CONCENTRATION,
                RiskCategory.COUNTERPARTY_CONCENTRATION.getDisplayName(),
                "ASSESSED",
                r04InsightCount,
                r04Highest,
                r04InsightCount == 0 ? "Customer receivables exposure is well-diversified." : null
        ));
    }

    // =========================================================================
    // R05 — RECURRING OPERATING CASH PRESSURE
    // =========================================================================
    private void evaluateRecurringCashPressure(
            UUID businessId,
            String currency,
            LocalDate asOfDate,
            int horizonDays,
            BigDecimal availableCash,
            Map<String, FinancialRiskInsight> uniqueInsights,
            List<RiskCategoryAssessment> categoryAssessments
    ) {
        LocalDate horizonEnd = asOfDate.plusDays(horizonDays);
        List<RecurringRule> activeRules = recurringRuleRepository.findByBusinessIdAndStatus(businessId, RecurringRuleStatus.ACTIVE).stream()
                .filter(r -> currency.equalsIgnoreCase(r.getCurrency()) && r.getEventType().getMovementDirection() == MovementDirection.DECREASE)
                .toList();

        // Collect existing occurrences in horizon to avoid double-counting
        List<RecurringRuleOccurrence> occurrences = recurringOccurrenceRepository.findByBusinessIdAndScheduledDateBetween(businessId, asOfDate, horizonEnd);
        Set<String> seenRuleDates = new HashSet<>();
        for (RecurringRuleOccurrence occ : occurrences) {
            seenRuleDates.add(occ.getRecurringRuleId() + ":" + occ.getScheduledDate());
        }

        BigDecimal totalRecurringExpenses = BigDecimal.ZERO;
        List<RiskEvidenceReference> r05Evidence = new ArrayList<>();

        // Add already scheduled non-skipped occurrences
        for (RecurringRuleOccurrence occ : occurrences) {
            if (occ.getStatus() == RecurringOccurrenceStatus.SCHEDULED || occ.getStatus() == RecurringOccurrenceStatus.DRAFT_READY) {
                totalRecurringExpenses = totalRecurringExpenses.add(occ.getAmount());
                if (r05Evidence.size() < 5) {
                    r05Evidence.add(new RiskEvidenceReference(
                            EvidenceType.RECURRING_RULE,
                            occ.getId().toString(),
                            "Scheduled recurring payment due " + occ.getScheduledDate(),
                            scale(occ.getAmount()),
                            occ.getScheduledDate(),
                            null,
                            Map.of("status", occ.getStatus().name())
                    ));
                }
            }
        }

        // Preview future occurrences from active recurring rules
        for (RecurringRule rule : activeRules) {
            if (rule.getNextDueDate() == null || rule.getFrequency() == null) continue;
            if (rule.getNextDueDate().isAfter(horizonEnd)) continue;

            LocalDate scheduleStart = rule.getNextDueDate().isBefore(asOfDate) ? asOfDate : rule.getNextDueDate();
            LocalDate ruleEnd = rule.getEndDate() != null && rule.getEndDate().isBefore(horizonEnd) ? rule.getEndDate() : horizonEnd;
            if (scheduleStart.isAfter(ruleEnd)) continue;

            List<LocalDate> scheduleDates = RecurrenceCalculator.previewSchedule(
                    scheduleStart,
                    ruleEnd,
                    rule.getFrequency(),
                    rule.getAnchorDayOfMonth(),
                    rule.getAnchorMonth(),
                    100
            );

            for (LocalDate pDate : scheduleDates) {
                if (pDate.isBefore(asOfDate) || pDate.isAfter(horizonEnd)) continue;
                String key = rule.getId() + ":" + pDate;
                if (!seenRuleDates.add(key)) continue;

                totalRecurringExpenses = totalRecurringExpenses.add(rule.getAmount());
                if (r05Evidence.size() < 5) {
                    r05Evidence.add(new RiskEvidenceReference(
                            EvidenceType.RECURRING_RULE,
                            rule.getId().toString(),
                            "Active recurring commitment: " + rule.getName() + " due " + pDate,
                            scale(rule.getAmount()),
                            pDate,
                            null,
                            Map.of("frequency", rule.getFrequency().name(), "eventType", rule.getEventType().name())
                    ));
                }
            }
        }

        int insightCount = 0;
        RiskSeverity highest = RiskSeverity.LOW;

        if (totalRecurringExpenses.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal cashRatio = availableCash.compareTo(BigDecimal.ZERO) > 0
                    ? totalRecurringExpenses.divide(availableCash, 4, RoundingMode.HALF_UP)
                    : BigDecimal.valueOf(1.00);

            if (availableCash.compareTo(BigDecimal.ZERO) <= 0 || cashRatio.compareTo(RiskThresholdPolicy.RECURRING_CASH_RATIO_HIGH_THRESHOLD) >= 0) {
                highest = RiskSeverity.HIGH;
            } else if (cashRatio.compareTo(RiskThresholdPolicy.RECURRING_CASH_RATIO_MEDIUM_THRESHOLD) >= 0) {
                highest = RiskSeverity.MEDIUM;
            }

            if (highest != RiskSeverity.LOW) {
                BigDecimal ratioPct = cashRatio.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
                String insightId = "RISK-R05-RECURRING-PRESSURE";
                FinancialRiskInsight insight = new FinancialRiskInsight(
                        insightId,
                        "R05_RECURRING_CASH_PRESSURE",
                        RiskCategory.RECURRING_OPERATIONS,
                        highest,
                        "Recurring Operating Expenses of " + currency + " " + scale(totalRecurringExpenses) + " Pressure Cash Buffer",
                        "Scheduled recurring commitments total " + currency + " " + scale(totalRecurringExpenses) + " over the next " + horizonDays + " days, representing " + ratioPct + "% of current cash reserves.",
                        "Fixed recurring overhead commitments reduce discretionary cash flow and leave little cushion for unexpected expenses or collection delays.",
                        currency,
                        scaleAmount(totalRecurringExpenses),
                        ratioPct,
                        "Next " + horizonDays + " days (" + asOfDate + " to " + horizonEnd + ")",
                        asOfDate,
                        horizonEnd,
                        r05Evidence,
                        new RiskActionRecommendation(
                                "REVIEW_RECURRING_COMMITMENTS",
                                "Review upcoming recurring operating payments",
                                "Audit scheduled subscriptions, service agreements, and periodic operational payments to identify opportunities for deferral or cost optimization.",
                                Urgency.PLANNING,
                                "/records?view=recurring"
                        ),
                        RiskCompleteness.COMPLETE
                );

                uniqueInsights.put(insightId, insight);
                insightCount++;
            }
        }

        categoryAssessments.add(new RiskCategoryAssessment(
                RiskCategory.RECURRING_OPERATIONS,
                RiskCategory.RECURRING_OPERATIONS.getDisplayName(),
                "ASSESSED",
                insightCount,
                highest,
                insightCount == 0 ? "Recurring expenses are within safe operating cash thresholds." : null
        ));
    }

    // =========================================================================
    // R06 — NEGATIVE ACTUAL CASH POSITION
    // =========================================================================
    private void evaluateNegativeActualCash(
            UUID businessId,
            String currency,
            Map<UUID, BigDecimal> accountBalances,
            Map<String, FinancialRiskInsight> uniqueInsights,
            List<RiskCategoryAssessment> categoryAssessments
    ) {
        int insightCount = 0;
        RiskSeverity highest = RiskSeverity.LOW;

        for (Map.Entry<UUID, BigDecimal> entry : accountBalances.entrySet()) {
            BigDecimal balance = entry.getValue();
            if (balance.compareTo(BigDecimal.ZERO) < 0) {
                UUID accountId = entry.getKey();
                Optional<FinancialAccount> accOpt = accountRepository.findByIdAndBusinessId(accountId, businessId);
                String accName = accOpt.isPresent() ? accOpt.get().getAccountName() : "Account " + accountId;

                String insightId = "RISK-R06-NEGATIVE-CASH-" + accountId;
                BigDecimal deficitAmount = balance.abs().setScale(2, RoundingMode.HALF_UP);

                List<RiskEvidenceReference> r06Evidence = List.of(new RiskEvidenceReference(
                        EvidenceType.FINANCIAL_ACCOUNT,
                        accountId.toString(),
                        "Authoritative ledger balance on " + accName + " is negative: " + currency + " " + scale(balance),
                        scale(balance),
                        LocalDate.now(BUSINESS_ZONE),
                        null,
                        Map.of("accountId", accountId.toString(), "accountName", accName, "actualBalance", balance)
                ));

                FinancialRiskInsight insight = new FinancialRiskInsight(
                        insightId,
                        "R06_NEGATIVE_ACTUAL_CASH",
                        RiskCategory.CASH_POSITION,
                        RiskSeverity.CRITICAL,
                        "Negative Actual Cash on " + accName + ": " + currency + " " + scale(balance),
                        "Account '" + accName + "' currently holds a negative ledger balance of " + currency + " " + scale(balance) + ". This is an actual posted condition, not a forecast.",
                        "A negative cash position indicates unrecorded deposits, unmanaged overdrafts, or bank account reconciliation divergence. Continued negative balances may trigger bank fees or service freezes.",
                        currency,
                        scaleAmount(deficitAmount),
                        null,
                        "Current actual balance",
                        null,
                        null,
                        r06Evidence,
                        new RiskActionRecommendation(
                                "RESTORE_ACCOUNT_LIQUIDITY",
                                "Immediately replenish negative account balance",
                                "Transfer funds from surplus accounts or record missing receipts to restore '" + accName + "' to positive standing.",
                                Urgency.IMMEDIATE,
                                "/finance/accounts"
                        ),
                        RiskCompleteness.COMPLETE
                );

                uniqueInsights.put(insightId, insight);
                insightCount++;
                highest = RiskSeverity.CRITICAL;
            }
        }

        categoryAssessments.add(new RiskCategoryAssessment(
                RiskCategory.CASH_POSITION,
                RiskCategory.CASH_POSITION.getDisplayName(),
                "ASSESSED",
                insightCount,
                highest,
                insightCount == 0 ? "All financial account balances are positive." : null
        ));
    }

    // =========================================================================
    // R07 — FINANCIAL DATA COMPLETENESS RISK
    // =========================================================================
    private void evaluateDataCompleteness(
            UUID businessId,
            Map<String, FinancialRiskInsight> uniqueInsights,
            List<RiskCategoryAssessment> categoryAssessments
    ) {
        // 1. Unscheduled active obligations (missing dueDate)
        List<FinancialObligation> unscheduledObs = obligationRepository.findByBusinessId(businessId).stream()
                .filter(o -> o.getStatus() == ObligationStatus.OPEN && o.getDueDate() == null)
                .toList();

        // 2. Pending unreviewed drafts
        List<RecordDraft> pendingDrafts = recordDraftRepository.findByBusinessIdAndStatus(businessId, DraftStatus.DRAFT);

        int insightCount = 0;
        RiskSeverity highest = RiskSeverity.LOW;

        if (!unscheduledObs.isEmpty() || !pendingDrafts.isEmpty()) {
            highest = !unscheduledObs.isEmpty() ? RiskSeverity.HIGH : RiskSeverity.MEDIUM;

            List<RiskEvidenceReference> r07Evidence = new ArrayList<>();
            for (FinancialObligation o : unscheduledObs.stream().limit(3).toList()) {
                r07Evidence.add(new RiskEvidenceReference(
                        EvidenceType.FINANCIAL_OBLIGATION,
                        o.getId().toString(),
                        "Open obligation without due date: " + (o.getReferenceNumber() != null ? o.getReferenceNumber() : o.getId()),
                        scale(o.getOriginalAmount()),
                        o.getIssueDate(),
                        null,
                        Map.of("direction", o.getDirection().name(), "missingDueDate", true)
                ));
            }

            for (RecordDraft d : pendingDrafts.stream().limit(3).toList()) {
                r07Evidence.add(new RiskEvidenceReference(
                        EvidenceType.RECORD_DRAFT,
                        d.getId().toString(),
                        "Pending OCR / statement draft awaiting review: " + (d.getDescription() != null ? d.getDescription() : d.getId()),
                        d.getAmountInput() != null ? d.getAmountInput() : "0.00",
                        d.getEffectiveDate(),
                        null,
                        Map.of("status", d.getStatus().name())
                ));
            }

            String insightId = "RISK-R07-DATA-COMPLETENESS";
            FinancialRiskInsight insight = new FinancialRiskInsight(
                    insightId,
                    "R07_DATA_COMPLETENESS",
                    RiskCategory.DATA_INTEGRITY,
                    highest,
                    "Data Completeness Risk: " + unscheduledObs.size() + " Unscheduled Obligations, " + pendingDrafts.size() + " Pending Drafts",
                    "Authoritative risk evaluation is constrained by missing data: " + unscheduledObs.size() + " open obligations lack due dates, and " + pendingDrafts.size() + " drafts remain unreviewed.",
                    "Without complete obligation schedules and confirmed transaction drafts, cash forecasting and risk assessments may underestimate exposure. Missing data does not indicate low risk.",
                    "PKR",
                    null,
                    null,
                    "Data hygiene status",
                    null,
                    null,
                    r07Evidence,
                    new RiskActionRecommendation(
                            "COMPLETE_FINANCIAL_RECORDS",
                            "Assign due dates and complete pending draft reviews",
                            "Review unconfirmed drafts in Review Drafts and set maturity dates on unscheduled invoices and payables.",
                            Urgency.SHORT_TERM,
                            "/records?view=review"
                    ),
                    RiskCompleteness.PARTIAL
            );

            uniqueInsights.put(insightId, insight);
            insightCount++;
        }

        categoryAssessments.add(new RiskCategoryAssessment(
                RiskCategory.DATA_INTEGRITY,
                RiskCategory.DATA_INTEGRITY.getDisplayName(),
                insightCount > 0 ? "DATA_LIMITED" : "ASSESSED",
                insightCount,
                highest,
                insightCount == 0 ? "Financial data completeness is verified across all modules." : "Assessment accuracy is constrained by pending reviews and unscheduled obligations."
        ));
    }

    // =========================================================================
    // R08 — BANK RECONCILIATION COVERAGE
    // =========================================================================
    private void evaluateBankReconciliation(
            UUID businessId,
            String currency,
            Map<String, FinancialRiskInsight> uniqueInsights,
            List<RiskCategoryAssessment> categoryAssessments
    ) {
        List<FinancialAccount> bankAccounts = accountRepository.findByBusinessId(businessId).stream()
                .filter(a -> a.getAccountType() == AccountType.BANK && a.getStatus() == AccountStatus.ACTIVE)
                .toList();

        if (bankAccounts.isEmpty()) {
            categoryAssessments.add(new RiskCategoryAssessment(
                    RiskCategory.BANK_RECONCILIATION,
                    RiskCategory.BANK_RECONCILIATION.getDisplayName(),
                    "NOT_ASSESSED",
                    0,
                    RiskSeverity.NOT_ASSESSED,
                    "No active bank accounts are registered for this business."
            ));
            return;
        }

        long totalEntries = 0;
        long totalUnmatched = 0;
        BigDecimal unmatchedSum = BigDecimal.ZERO;
        List<RiskEvidenceReference> r08Evidence = new ArrayList<>();

        for (FinancialAccount acc : bankAccounts) {
            long entries = statementEntryRepository.countByBusinessIdAndAccountId(businessId, acc.getId());
            totalEntries += entries;

            long unmatched = statementEntryRepository.countByBusinessIdAndAccountIdAndStatus(businessId, acc.getId(), StatementEntryStatus.UNMATCHED);
            totalUnmatched += unmatched;

            List<BankStatementEntryRepository.EntryStatusSum> statusSums = statementEntryRepository.sumAmountsByStatus(businessId, acc.getId());
            if (statusSums != null) {
                for (BankStatementEntryRepository.EntryStatusSum ss : statusSums) {
                    if (ss.getStatus() == StatementEntryStatus.UNMATCHED && ss.getTotalAmount() != null) {
                        unmatchedSum = unmatchedSum.add(ss.getTotalAmount());
                    }
                }
            }

            if (unmatched > 0 && r08Evidence.size() < 5) {
                r08Evidence.add(new RiskEvidenceReference(
                        EvidenceType.BANK_STATEMENT_ENTRY,
                        acc.getId().toString(),
                        "Bank account '" + acc.getAccountName() + "' has " + unmatched + " unmatched statement entries",
                        scale(unmatchedSum),
                        LocalDate.now(BUSINESS_ZONE),
                        null,
                        Map.of("accountId", acc.getId().toString(), "accountName", acc.getAccountName(), "unmatchedCount", unmatched)
                ));
            }
        }

        if (totalEntries == 0) {
            categoryAssessments.add(new RiskCategoryAssessment(
                    RiskCategory.BANK_RECONCILIATION,
                    RiskCategory.BANK_RECONCILIATION.getDisplayName(),
                    "NOT_ASSESSED",
                    0,
                    RiskSeverity.NOT_ASSESSED,
                    "No bank statement entries have been imported for registered bank accounts."
            ));
            return;
        }

        int insightCount = 0;
        RiskSeverity highest = RiskSeverity.LOW;

        if (totalUnmatched > 0) {
            highest = (totalUnmatched >= RiskThresholdPolicy.BANK_RECON_UNMATCHED_COUNT_HIGH_THRESHOLD ||
                    unmatchedSum.compareTo(RiskThresholdPolicy.BANK_RECON_UNMATCHED_AMOUNT_HIGH_THRESHOLD) >= 0)
                    ? RiskSeverity.HIGH
                    : RiskSeverity.MEDIUM;

            String insightId = "RISK-R08-RECONCILIATION-UNMATCHED";
            FinancialRiskInsight insight = new FinancialRiskInsight(
                    insightId,
                    "R08_BANK_RECONCILIATION_COVERAGE",
                    RiskCategory.BANK_RECONCILIATION,
                    highest,
                    "Unreconciled Bank Statements: " + totalUnmatched + " Unmatched Entries (" + currency + " " + scale(unmatchedSum) + ")",
                    totalUnmatched + " imported bank statement entries totaling " + currency + " " + scale(unmatchedSum) + " have not been matched with ledger records.",
                    "Unmatched bank statement items create divergence between actual bank funds and ledger cash, obscuring real liquidity and delaying financial reporting.",
                    currency,
                    scaleAmount(unmatchedSum),
                    null,
                    "Unreconciled imported statements",
                    null,
                    null,
                    r08Evidence,
                    new RiskActionRecommendation(
                            "PERFORM_BANK_RECONCILIATION",
                            "Match unresolved bank statement entries",
                            "Open Bank Reconciliation to match open statement lines with movements or record missing bank transactions.",
                            Urgency.SHORT_TERM,
                            "/records?view=reconciliation"
                    ),
                    RiskCompleteness.COMPLETE
            );

            uniqueInsights.put(insightId, insight);
            insightCount++;
        }

        categoryAssessments.add(new RiskCategoryAssessment(
                RiskCategory.BANK_RECONCILIATION,
                RiskCategory.BANK_RECONCILIATION.getDisplayName(),
                "ASSESSED",
                insightCount,
                highest,
                insightCount == 0 ? "All imported bank statement entries are fully reconciled." : null
        ));
    }

    // =========================================================================
    // AUTHORIZATION & VALIDATION HELPERS
    // =========================================================================
    private void requirePermission(BusinessAccessContext context) {
        if (context == null || context.businessId() == null) {
            throw new AccessDeniedException("Authenticated active business context is required");
        }
        if (authorizationService != null && !authorizationService.hasRolePermission(context.role(), BusinessPermission.FINANCIAL_DATA_READ)) {
            throw new AccessDeniedException("Role " + context.role() + " does not have permission: " + BusinessPermission.FINANCIAL_DATA_READ);
        }
    }

    private LocalDate parseAsOfDate(String asOfDateStr) {
        if (asOfDateStr == null || asOfDateStr.trim().isEmpty()) {
            return LocalDate.now(BUSINESS_ZONE);
        }
        try {
            return LocalDate.parse(asOfDateStr.trim());
        } catch (DateTimeParseException e) {
            throw new RiskValidationException("asOfDate", "Invalid date format. Expected YYYY-MM-DD");
        }
    }

    private int sanitizeHorizonDays(Integer horizonDaysParam) {
        if (horizonDaysParam == null) {
            return RiskThresholdPolicy.DEFAULT_HORIZON_DAYS;
        }
        if (horizonDaysParam < RiskThresholdPolicy.MIN_HORIZON_DAYS || horizonDaysParam > RiskThresholdPolicy.MAX_HORIZON_DAYS) {
            throw new RiskValidationException("horizonDays", "Horizon days must be between " + RiskThresholdPolicy.MIN_HORIZON_DAYS + " and " + RiskThresholdPolicy.MAX_HORIZON_DAYS);
        }
        return horizonDaysParam;
    }

    private String sanitizeCurrency(String currencyParam) {
        if (currencyParam == null || currencyParam.trim().isEmpty()) {
            return RiskThresholdPolicy.DEFAULT_CURRENCY;
        }
        String clean = currencyParam.trim().toUpperCase(Locale.ROOT);
        if (clean.length() != 3) {
            throw new RiskValidationException("currency", "Currency must be a 3-letter ISO code");
        }
        return clean;
    }

    private RiskCategory parseCategoryFilter(String categoryParam) {
        if (categoryParam == null || categoryParam.trim().isEmpty()) {
            return null;
        }
        try {
            return RiskCategory.valueOf(categoryParam.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new RiskValidationException("category", "Invalid risk category: " + categoryParam);
        }
    }

    private RiskSeverity parseSeverityFilter(String severityParam) {
        if (severityParam == null || severityParam.trim().isEmpty()) {
            return null;
        }
        try {
            return RiskSeverity.valueOf(severityParam.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new RiskValidationException("severity", "Invalid risk severity: " + severityParam);
        }
    }

    private Map<UUID, BigDecimal> computeAccountBalances(UUID businessId, LocalDate asOfDate, String currency) {
        Map<UUID, BigDecimal> balances = new HashMap<>();
        List<FinancialAccount> accounts = accountRepository.findByBusinessId(businessId).stream()
                .filter(a -> a.getStatus() == AccountStatus.ACTIVE && currency.equalsIgnoreCase(a.getCurrency()))
                .toList();

        Map<UUID, BigDecimal> netMovements = new HashMap<>();
        List<AccountMovementSum> movementSums = movementRepository.findMovementSumsByBusinessIdAndCutoffDate(businessId, asOfDate);
        if (movementSums != null) {
            for (AccountMovementSum sum : movementSums) {
                if (sum == null || sum.getAccountId() == null || sum.getTotalAmount() == null) continue;
                BigDecimal current = netMovements.getOrDefault(sum.getAccountId(), BigDecimal.ZERO);
                if (sum.getMovementDirection() == MovementDirection.INCREASE) {
                    netMovements.put(sum.getAccountId(), current.add(sum.getTotalAmount()));
                } else if (sum.getMovementDirection() == MovementDirection.DECREASE) {
                    netMovements.put(sum.getAccountId(), current.subtract(sum.getTotalAmount()));
                }
            }
        }

        for (FinancialAccount acc : accounts) {
            BigDecimal opening = acc.getOpeningBalance() != null ? acc.getOpeningBalance() : BigDecimal.ZERO;
            BigDecimal net = netMovements.getOrDefault(acc.getId(), BigDecimal.ZERO);
            balances.put(acc.getId(), opening.add(net).setScale(2, RoundingMode.HALF_UP));
        }

        return balances;
    }

    private static int severityRank(RiskSeverity severity) {
        return switch (severity) {
            case CRITICAL -> 0;
            case HIGH -> 1;
            case MEDIUM -> 2;
            case LOW -> 3;
            case NOT_ASSESSED -> 4;
        };
    }

    private static String scale(BigDecimal val) {
        if (val == null) return "0.00";
        return val.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static BigDecimal scaleAmount(BigDecimal val) {
        if (val == null) return null;
        return val.setScale(2, RoundingMode.HALF_UP);
    }
}
