package com.app.sme_health_backend.forecast.service;

import com.app.sme_health_backend.accounting.draft.DraftStatus;
import com.app.sme_health_backend.accounting.draft.RecordDraft;
import com.app.sme_health_backend.accounting.draft.RecordDraftRepository;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.MovementDirection;
import com.app.sme_health_backend.accounting.model.TransactionEventType;
import com.app.sme_health_backend.accounting.repository.AccountMovementRepository;
import com.app.sme_health_backend.accounting.repository.AccountMovementSum;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.counterparty.Counterparty;
import com.app.sme_health_backend.counterparty.CounterpartyRepository;
import com.app.sme_health_backend.financialquery.model.DataCompleteness;
import com.app.sme_health_backend.forecast.dto.*;
import com.app.sme_health_backend.forecast.exception.ForecastValidationException;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.obligation.*;
import com.app.sme_health_backend.obligation.aging.ObligationDirection;
import com.app.sme_health_backend.recurring.calculator.RecurrenceCalculator;
import com.app.sme_health_backend.recurring.entity.RecurringRule;
import com.app.sme_health_backend.recurring.entity.RecurringRuleOccurrence;
import com.app.sme_health_backend.recurring.model.RecurringOccurrenceStatus;
import com.app.sme_health_backend.recurring.model.RecurringRuleStatus;
import com.app.sme_health_backend.recurring.repository.RecurringRuleOccurrenceRepository;
import com.app.sme_health_backend.recurring.repository.RecurringRuleRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class DailyCashForecastService {

    private static final String CONTRACT_VERSION = "1.0";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Karachi");
    private static final int DEFAULT_HORIZON_DAYS = 30;
    private static final int MIN_HORIZON_DAYS = 1;
    private static final int MAX_HORIZON_DAYS = 365;

    private final FinancialAccountRepository accountRepository;
    private final AccountMovementRepository movementRepository;
    private final FinancialObligationRepository obligationRepository;
    private final ObligationPaymentAllocationRepository allocationRepository;
    private final ObligationPaymentScheduleRepository scheduleRepository;
    private final CounterpartyRepository counterpartyRepository;
    private final RecurringRuleRepository recurringRuleRepository;
    private final RecurringRuleOccurrenceRepository recurringOccurrenceRepository;
    private final RecordDraftRepository recordDraftRepository;
    private final BusinessAuthorizationService authorizationService;

    public DailyCashForecastService(
            FinancialAccountRepository accountRepository,
            AccountMovementRepository movementRepository,
            FinancialObligationRepository obligationRepository,
            ObligationPaymentAllocationRepository allocationRepository,
            ObligationPaymentScheduleRepository scheduleRepository,
            CounterpartyRepository counterpartyRepository,
            RecurringRuleRepository recurringRuleRepository,
            RecurringRuleOccurrenceRepository recurringOccurrenceRepository,
            RecordDraftRepository recordDraftRepository,
            BusinessAuthorizationService authorizationService
    ) {
        this.accountRepository = accountRepository;
        this.movementRepository = movementRepository;
        this.obligationRepository = obligationRepository;
        this.allocationRepository = allocationRepository;
        this.scheduleRepository = scheduleRepository;
        this.counterpartyRepository = counterpartyRepository;
        this.recurringRuleRepository = recurringRuleRepository;
        this.recurringOccurrenceRepository = recurringOccurrenceRepository;
        this.recordDraftRepository = recordDraftRepository;
        this.authorizationService = authorizationService;
    }

    public DailyCashForecastResponse getDailyCashForecast(
            BusinessAccessContext context,
            String currencyParam,
            Integer horizonDaysParam,
            String asOfDateStr
    ) {
        requirePermission(context, BusinessPermission.FINANCIAL_TRANSACTION_READ);
        String currency = sanitizeCurrency(currencyParam);
        int horizonDays = sanitizeHorizonDays(horizonDaysParam);
        LocalDate asOfDate = parseAsOfDate(asOfDateStr);

        return computeDailyForecast(context.businessId(), currency, asOfDate, horizonDays, null);
    }

    public ForecastScenarioResponse previewScenario(
            BusinessAccessContext context,
            ForecastScenarioRequest req
    ) {
        requirePermission(context, BusinessPermission.FINANCIAL_TRANSACTION_READ);
        if (req == null) {
            throw new ForecastValidationException("request", "Scenario request body cannot be null");
        }
        String currency = sanitizeCurrency(req.currency());
        int horizonDays = sanitizeHorizonDays(req.horizonDays());
        LocalDate asOfDate = parseAsOfDate(req.asOfDate());

        validateScenarioParameters(req);

        DailyCashForecastResponse baseline = computeDailyForecast(
                context.businessId(), currency, asOfDate, horizonDays, null
        );

        DailyCashForecastResponse scenario = computeDailyForecast(
                context.businessId(), currency, asOfDate, horizonDays, req
        );

        ForecastScenarioComparison comparison = buildComparison(baseline, scenario, req);

        return new ForecastScenarioResponse(CONTRACT_VERSION, baseline, scenario, comparison);
    }

    private DailyCashForecastResponse computeDailyForecast(
            UUID businessId,
            String currency,
            LocalDate asOfDate,
            int horizonDays,
            ForecastScenarioRequest scenarioReq
    ) {
        LocalDate horizonEndDate = asOfDate.plusDays(horizonDays);

        // 1. Authoritative starting cash position as of asOfDate
        BigDecimal startingCash = computeAuthoritativeStartingCash(businessId, currency, asOfDate);

        // 2. Load open obligations and effective payment allocations
        List<FinancialObligation> openObligations = obligationRepository.findByBusinessId(businessId).stream()
                .filter(o -> o.getStatus() == ObligationStatus.OPEN)
                .filter(o -> currency.equalsIgnoreCase(o.getCurrency()))
                .toList();

        Map<UUID, BigDecimal> paidByObligation = new HashMap<>();
        List<ObligationPaymentAllocation> allAllocations = allocationRepository.findByBusinessId(businessId);
        for (ObligationPaymentAllocation a : allAllocations) {
            if (a.getStatus() == AllocationStatus.EFFECTIVE) {
                paidByObligation.merge(a.getObligationId(), a.getAmount(), BigDecimal::add);
            }
        }

        // Counterparty map
        Set<UUID> cpIds = openObligations.stream()
                .map(FinancialObligation::getCounterpartyId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, Counterparty> cpMap = cpIds.isEmpty() ? Collections.emptyMap() :
                counterpartyRepository.findAllById(cpIds).stream()
                        .filter(c -> businessId.equals(c.getBusinessId()))
                        .collect(Collectors.toMap(Counterparty::getId, c -> c));

        // Planned payment schedules
        List<ObligationPaymentSchedule> allPlannedSchedules = scheduleRepository.findByBusinessId(businessId).stream()
                .filter(s -> s.getStatus() == ScheduleStatus.PLANNED)
                .sorted(Comparator.comparing(ObligationPaymentSchedule::getExpectedDate)
                        .thenComparingInt(ObligationPaymentSchedule::getInstallmentNumber))
                .toList();

        Map<UUID, List<ObligationPaymentSchedule>> schedulesByObligation = new LinkedHashMap<>();
        for (ObligationPaymentSchedule s : allPlannedSchedules) {
            schedulesByObligation.computeIfAbsent(s.getObligationId(), k -> new ArrayList<>()).add(s);
        }

        Map<LocalDate, List<ForecastItemDetail>> itemsByDate = new HashMap<>();
        Map<LocalDate, BigDecimal> recvByDate = new HashMap<>();
        Map<LocalDate, BigDecimal> payByDate = new HashMap<>();
        Map<LocalDate, BigDecimal> recurReceiptsByDate = new HashMap<>();
        Map<LocalDate, BigDecimal> recurExpensesByDate = new HashMap<>();

        BigDecimal overdueReceivablesTotal = BigDecimal.ZERO;
        BigDecimal overduePayablesTotal = BigDecimal.ZERO;
        int missingDueDatesCount = 0;
        BigDecimal missingDueDatesExposure = BigDecimal.ZERO;

        Map<UUID, BigDecimal> remainingPlannedByObligation = new HashMap<>();

        // Process scheduled installments with chronological allocation absorption
        for (Map.Entry<UUID, List<ObligationPaymentSchedule>> entry : schedulesByObligation.entrySet()) {
            FinancialObligation ob = openObligations.stream()
                    .filter(o -> o.getId().equals(entry.getKey()))
                    .findFirst()
                    .orElse(null);
            if (ob == null) continue;

            BigDecimal paid = paidByObligation.getOrDefault(ob.getId(), BigDecimal.ZERO);
            BigDecimal outstanding = ob.getOriginalAmount().subtract(paid);
            if (outstanding.compareTo(BigDecimal.ZERO) < 0) outstanding = BigDecimal.ZERO;
            if (outstanding.compareTo(BigDecimal.ZERO) == 0) continue;

            BigDecimal remainingPaidToAbsorb = paid;
            BigDecimal remainingOutstandingCap = outstanding;
            BigDecimal totalRemainingPlannedForOb = BigDecimal.ZERO;

            for (ObligationPaymentSchedule s : entry.getValue()) {
                BigDecimal installmentExp = s.getExpectedAmount();
                if (remainingPaidToAbsorb.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal absorbed = remainingPaidToAbsorb.min(installmentExp);
                    installmentExp = installmentExp.subtract(absorbed);
                    remainingPaidToAbsorb = remainingPaidToAbsorb.subtract(absorbed);
                }
                if (installmentExp.compareTo(remainingOutstandingCap) > 0) {
                    installmentExp = remainingOutstandingCap;
                }
                if (installmentExp.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                remainingOutstandingCap = remainingOutstandingCap.subtract(installmentExp);
                totalRemainingPlannedForOb = totalRemainingPlannedForOb.add(installmentExp);

                Counterparty cp = cpMap.get(ob.getCounterpartyId());
                String cpName = cp != null ? cp.getDisplayName() : "Unknown Counterparty";

                if (s.getExpectedDate().isBefore(asOfDate)) {
                    if (ob.getDirection() == ObligationDirection.RECEIVABLE) {
                        overdueReceivablesTotal = overdueReceivablesTotal.add(installmentExp);
                        if (scenarioReq != null && Boolean.TRUE.equals(scenarioReq.includeOverdueReceivables())) {
                            int spread = Math.max(1, scenarioReq.overdueSpreadDays() != null ? scenarioReq.overdueSpreadDays() : 1);
                            BigDecimal slice = installmentExp.divide(BigDecimal.valueOf(spread), 2, RoundingMode.HALF_UP);
                            for (int i = 0; i < spread; i++) {
                                LocalDate spreadDate = asOfDate.plusDays(i);
                                if (!spreadDate.isAfter(horizonEndDate)) {
                                    recvByDate.merge(spreadDate, slice, BigDecimal::add);
                                    itemsByDate.computeIfAbsent(spreadDate, k -> new ArrayList<>()).add(new ForecastItemDetail(
                                            "OVERDUE_RECEIVABLE_RECOVERED",
                                            s.getId().toString(),
                                            "RECEIVABLE",
                                            cpName,
                                            ob.getReferenceNumber(),
                                            "Recovered overdue installment (Spread " + (i + 1) + "/" + spread + ")",
                                            scaleAmount(slice),
                                            s.getExpectedDate(),
                                            spreadDate,
                                            true
                                    ));
                                }
                            }
                        }
                    } else {
                        overduePayablesTotal = overduePayablesTotal.add(installmentExp);
                    }
                } else {
                    // Due within or after evaluation date
                    LocalDate forecastDate = s.getExpectedDate();
                    boolean shifted = false;
                    BigDecimal finalAmount = installmentExp;

                    if (ob.getDirection() == ObligationDirection.RECEIVABLE) {
                        if (scenarioReq != null && scenarioReq.collectionDelayDays() != null && scenarioReq.collectionDelayDays() > 0) {
                            forecastDate = forecastDate.plusDays(scenarioReq.collectionDelayDays());
                            shifted = true;
                        }
                        if (scenarioReq != null && scenarioReq.collectionPercentage() != null && scenarioReq.collectionPercentage().compareTo(new BigDecimal("100.00")) < 0) {
                            finalAmount = finalAmount.multiply(scenarioReq.collectionPercentage())
                                    .divide(new BigDecimal("100.00"), 2, RoundingMode.HALF_UP);
                        }
                    } else {
                        if (scenarioReq != null && scenarioReq.paymentDelayDays() != null && scenarioReq.paymentDelayDays() > 0) {
                            forecastDate = forecastDate.plusDays(scenarioReq.paymentDelayDays());
                            shifted = true;
                        }
                    }

                    if (!forecastDate.isBefore(asOfDate) && !forecastDate.isAfter(horizonEndDate) && finalAmount.compareTo(BigDecimal.ZERO) > 0) {
                        if (ob.getDirection() == ObligationDirection.RECEIVABLE) {
                            recvByDate.merge(forecastDate, finalAmount, BigDecimal::add);
                        } else {
                            payByDate.merge(forecastDate, finalAmount, BigDecimal::add);
                        }

                        itemsByDate.computeIfAbsent(forecastDate, k -> new ArrayList<>()).add(new ForecastItemDetail(
                                "SCHEDULED_INSTALLMENT",
                                s.getId().toString(),
                                ob.getDirection().name(),
                                cpName,
                                ob.getReferenceNumber(),
                                s.getNotes() != null ? s.getNotes() : "Installment #" + s.getInstallmentNumber(),
                                scaleAmount(finalAmount),
                                s.getExpectedDate(),
                                forecastDate,
                                shifted
                        ));
                    }
                }
            }
            remainingPlannedByObligation.put(ob.getId(), totalRemainingPlannedForOb);
        }

        // Process unscheduled residual obligation exposure
        for (FinancialObligation ob : openObligations) {
            BigDecimal paid = paidByObligation.getOrDefault(ob.getId(), BigDecimal.ZERO);
            BigDecimal outstanding = ob.getOriginalAmount().subtract(paid);
            if (outstanding.compareTo(BigDecimal.ZERO) < 0) outstanding = BigDecimal.ZERO;
            if (outstanding.compareTo(BigDecimal.ZERO) == 0) continue;

            BigDecimal plannedTotal = remainingPlannedByObligation.getOrDefault(ob.getId(), BigDecimal.ZERO);
            BigDecimal unscheduledResidual = outstanding.subtract(plannedTotal);
            if (unscheduledResidual.compareTo(BigDecimal.ZERO) <= 0) continue;

            Counterparty cp = cpMap.get(ob.getCounterpartyId());
            String cpName = cp != null ? cp.getDisplayName() : "Unknown Counterparty";

            if (ob.getDueDate() == null) {
                missingDueDatesCount++;
                missingDueDatesExposure = missingDueDatesExposure.add(unscheduledResidual);
                continue;
            }

            if (ob.getDueDate().isBefore(asOfDate)) {
                if (ob.getDirection() == ObligationDirection.RECEIVABLE) {
                    overdueReceivablesTotal = overdueReceivablesTotal.add(unscheduledResidual);
                    if (scenarioReq != null && Boolean.TRUE.equals(scenarioReq.includeOverdueReceivables())) {
                        int spread = Math.max(1, scenarioReq.overdueSpreadDays() != null ? scenarioReq.overdueSpreadDays() : 1);
                        BigDecimal slice = unscheduledResidual.divide(BigDecimal.valueOf(spread), 2, RoundingMode.HALF_UP);
                        for (int i = 0; i < spread; i++) {
                            LocalDate spreadDate = asOfDate.plusDays(i);
                            if (!spreadDate.isAfter(horizonEndDate)) {
                                recvByDate.merge(spreadDate, slice, BigDecimal::add);
                                itemsByDate.computeIfAbsent(spreadDate, k -> new ArrayList<>()).add(new ForecastItemDetail(
                                        "OVERDUE_RECEIVABLE_RECOVERED",
                                        ob.getId().toString(),
                                        "RECEIVABLE",
                                        cpName,
                                        ob.getReferenceNumber(),
                                        "Recovered overdue obligation (Spread " + (i + 1) + "/" + spread + ")",
                                        scaleAmount(slice),
                                        ob.getDueDate(),
                                        spreadDate,
                                        true
                                ));
                            }
                        }
                    }
                } else {
                    overduePayablesTotal = overduePayablesTotal.add(unscheduledResidual);
                }
            } else {
                LocalDate forecastDate = ob.getDueDate();
                boolean shifted = false;
                BigDecimal finalAmount = unscheduledResidual;

                if (ob.getDirection() == ObligationDirection.RECEIVABLE) {
                    if (scenarioReq != null && scenarioReq.collectionDelayDays() != null && scenarioReq.collectionDelayDays() > 0) {
                        forecastDate = forecastDate.plusDays(scenarioReq.collectionDelayDays());
                        shifted = true;
                    }
                    if (scenarioReq != null && scenarioReq.collectionPercentage() != null && scenarioReq.collectionPercentage().compareTo(new BigDecimal("100.00")) < 0) {
                        finalAmount = finalAmount.multiply(scenarioReq.collectionPercentage())
                                .divide(new BigDecimal("100.00"), 2, RoundingMode.HALF_UP);
                    }
                } else {
                    if (scenarioReq != null && scenarioReq.paymentDelayDays() != null && scenarioReq.paymentDelayDays() > 0) {
                        forecastDate = forecastDate.plusDays(scenarioReq.paymentDelayDays());
                        shifted = true;
                    }
                }

                if (!forecastDate.isBefore(asOfDate) && !forecastDate.isAfter(horizonEndDate) && finalAmount.compareTo(BigDecimal.ZERO) > 0) {
                    if (ob.getDirection() == ObligationDirection.RECEIVABLE) {
                        recvByDate.merge(forecastDate, finalAmount, BigDecimal::add);
                    } else {
                        payByDate.merge(forecastDate, finalAmount, BigDecimal::add);
                    }

                    itemsByDate.computeIfAbsent(forecastDate, k -> new ArrayList<>()).add(new ForecastItemDetail(
                            "OBLIGATION_DUE",
                            ob.getId().toString(),
                            ob.getDirection().name(),
                            cpName,
                            ob.getReferenceNumber(),
                            ob.getDescription() != null ? ob.getDescription() : "Unscheduled residual exposure",
                            scaleAmount(finalAmount),
                            ob.getDueDate(),
                            forecastDate,
                            shifted
                    ));
                }
            }
        }

        // 3. Process recurring rules and occurrences
        List<RecurringRule> activeRules = recurringRuleRepository.findByBusinessIdAndStatus(businessId, RecurringRuleStatus.ACTIVE).stream()
                .filter(r -> currency.equalsIgnoreCase(r.getCurrency()))
                .toList();
        List<RecurringRuleOccurrence> occurrencesInWindow = recurringOccurrenceRepository.findByBusinessIdAndScheduledDateBetween(
                businessId, asOfDate, horizonEndDate
        );

        Set<String> seenRuleDates = new HashSet<>();

        // Include occurrences already generated in window (SCHEDULED or DRAFT_READY)
        for (RecurringRuleOccurrence occ : occurrencesInWindow) {
            RecurringRule rule = activeRules.stream()
                    .filter(r -> r.getId().equals(occ.getRecurringRuleId()))
                    .findFirst()
                    .orElse(null);
            if (rule == null) continue;

            String ruleDateKey = rule.getId() + ":" + occ.getScheduledDate();
            if (!seenRuleDates.add(ruleDateKey)) {
                continue;
            }

            // Only SCHEDULED and DRAFT_READY represent future unposted cash flows.
            // SKIPPED, POSTED, and REVERSED remain in seenRuleDates to prevent template duplication,
            // but do not contribute to future cash flows.
            if (occ.getStatus() != RecurringOccurrenceStatus.SCHEDULED && occ.getStatus() != RecurringOccurrenceStatus.DRAFT_READY) {
                continue;
            }

            BigDecimal occAmount = occ.getAmount();
            boolean isReceipt = rule.getEventType() == TransactionEventType.DIRECT_SALE_RECEIPT;

            if (isReceipt) {
                recurReceiptsByDate.merge(occ.getScheduledDate(), occAmount, BigDecimal::add);
            } else {
                recurExpensesByDate.merge(occ.getScheduledDate(), occAmount, BigDecimal::add);
            }

            itemsByDate.computeIfAbsent(occ.getScheduledDate(), k -> new ArrayList<>()).add(new ForecastItemDetail(
                    "RECURRING_RULE_OCCURRENCE",
                    occ.getId().toString(),
                    isReceipt ? "RECEIVABLE" : "PAYABLE",
                    "Recurring Schedule",
                    rule.getName(),
                    "Scheduled occurrence: " + rule.getName(),
                    scaleAmount(occAmount),
                    occ.getScheduledDate(),
                    occ.getScheduledDate(),
                    false
            ));
        }

        // Preview future occurrences from active recurring rules
        for (RecurringRule rule : activeRules) {
            if (rule.getNextDueDate() == null || rule.getFrequency() == null) continue;
            if (rule.getNextDueDate().isAfter(horizonEndDate)) continue;

            LocalDate scheduleStart = rule.getNextDueDate().isBefore(asOfDate) ? asOfDate : rule.getNextDueDate();
            LocalDate ruleEnd = rule.getEndDate() != null && rule.getEndDate().isBefore(horizonEndDate) ? rule.getEndDate() : horizonEndDate;
            if (scheduleStart.isAfter(ruleEnd)) continue;

            List<LocalDate> scheduleDates = RecurrenceCalculator.previewSchedule(
                    scheduleStart,
                    ruleEnd,
                    rule.getFrequency(),
                    rule.getAnchorDayOfMonth(),
                    rule.getAnchorMonth(),
                    100
            );

            boolean isReceipt = rule.getEventType() == TransactionEventType.DIRECT_SALE_RECEIPT;

            for (LocalDate pDate : scheduleDates) {
                if (pDate.isBefore(asOfDate) || pDate.isAfter(horizonEndDate)) continue;

                String ruleDateKey = rule.getId() + ":" + pDate;
                if (!seenRuleDates.add(ruleDateKey)) {
                    continue;
                }

                BigDecimal amt = rule.getAmount();
                if (isReceipt) {
                    recurReceiptsByDate.merge(pDate, amt, BigDecimal::add);
                } else {
                    recurExpensesByDate.merge(pDate, amt, BigDecimal::add);
                }

                itemsByDate.computeIfAbsent(pDate, k -> new ArrayList<>()).add(new ForecastItemDetail(
                        "RECURRING_RULE",
                        rule.getId().toString(),
                        isReceipt ? "RECEIVABLE" : "PAYABLE",
                        "Recurring Template",
                        rule.getName(),
                        "Recurring projection: " + rule.getName(),
                        scaleAmount(amt),
                        pDate,
                        pDate,
                        false
                ));
            }
        }

        // 4. Summarize unposted drafts
        List<RecordDraft> pendingDrafts = recordDraftRepository.findByBusinessIdAndStatus(businessId, DraftStatus.DRAFT);
        BigDecimal draftsInflow = BigDecimal.ZERO;
        BigDecimal draftsOutflow = BigDecimal.ZERO;
        for (RecordDraft d : pendingDrafts) {
            if (d.getAmountInput() == null || d.getAmountInput().isBlank()) continue;
            try {
                BigDecimal parsed = new BigDecimal(d.getAmountInput().trim());
                if (d.getEventType() == TransactionEventType.DIRECT_SALE_RECEIPT || d.getEventType() == TransactionEventType.RECEIVABLE_COLLECTION) {
                    draftsInflow = draftsInflow.add(parsed);
                } else if (d.getEventType() == TransactionEventType.OPERATING_EXPENSE_PAYMENT || d.getEventType() == TransactionEventType.PAYABLE_SETTLEMENT) {
                    draftsOutflow = draftsOutflow.add(parsed);
                }
            } catch (NumberFormatException ignored) {
                // Ignore unparseable draft input
            }
        }

        // 5. Build daily projection rows
        List<DailyForecastRow> rows = new ArrayList<>(horizonDays + 1);
        BigDecimal runningCash = startingCash;

        BigDecimal totalInflows = BigDecimal.ZERO;
        BigDecimal totalOutflows = BigDecimal.ZERO;
        BigDecimal minimumProjectedCash = startingCash;
        LocalDate minimumCashDate = asOfDate;
        LocalDate firstDeficitDate = null;
        BigDecimal maximumExpectedShortfall = BigDecimal.ZERO;

        for (int day = 0; day <= horizonDays; day++) {
            LocalDate currentDate = asOfDate.plusDays(day);
            BigDecimal opening = runningCash;

            BigDecimal expRecv = recvByDate.getOrDefault(currentDate, BigDecimal.ZERO);
            BigDecimal expRecurIn = recurReceiptsByDate.getOrDefault(currentDate, BigDecimal.ZERO);
            BigDecimal dayInflow = expRecv.add(expRecurIn);

            BigDecimal expPay = payByDate.getOrDefault(currentDate, BigDecimal.ZERO);
            BigDecimal expRecurOut = recurExpensesByDate.getOrDefault(currentDate, BigDecimal.ZERO);
            BigDecimal dayOutflow = expPay.add(expRecurOut);

            BigDecimal dayNet = dayInflow.subtract(dayOutflow);
            BigDecimal closing = opening.add(dayNet);

            boolean isDeficit = closing.compareTo(BigDecimal.ZERO) < 0;
            BigDecimal deficitAmt = isDeficit ? closing.abs() : BigDecimal.ZERO;

            totalInflows = totalInflows.add(dayInflow);
            totalOutflows = totalOutflows.add(dayOutflow);

            if (closing.compareTo(minimumProjectedCash) < 0) {
                minimumProjectedCash = closing;
                minimumCashDate = currentDate;
            }

            if (isDeficit && firstDeficitDate == null) {
                firstDeficitDate = currentDate;
            }

            if (deficitAmt.compareTo(maximumExpectedShortfall) > 0) {
                maximumExpectedShortfall = deficitAmt;
            }

            List<ForecastItemDetail> dayItems = itemsByDate.getOrDefault(currentDate, Collections.emptyList());
            dayItems.sort(Comparator.comparing(ForecastItemDetail::direction).reversed()
                    .thenComparing(ForecastItemDetail::amount));

            rows.add(new DailyForecastRow(
                    currentDate,
                    day,
                    scaleAmount(opening),
                    scaleAmount(expRecv),
                    scaleAmount(expRecurIn),
                    scaleAmount(dayInflow),
                    scaleAmount(expPay),
                    scaleAmount(expRecurOut),
                    scaleAmount(dayOutflow),
                    scaleAmount(dayNet),
                    scaleAmount(closing),
                    isDeficit,
                    scaleAmount(deficitAmt),
                    dayItems
            ));

            runningCash = closing;
        }

        BigDecimal netProjectedMovement = totalInflows.subtract(totalOutflows);

        // 6. Build liquidity warnings
        List<ForecastLiquidityWarning> warnings = new ArrayList<>();
        if (firstDeficitDate != null) {
            warnings.add(new ForecastLiquidityWarning(
                    "LIQUIDITY_DEFICIT",
                    "CRITICAL",
                    "Projected Cash Deficit",
                    "Cash balance is projected to become negative on " + firstDeficitDate + " with a maximum projected shortfall of " + currency + " " + scaleAmount(maximumExpectedShortfall) + ".",
                    firstDeficitDate,
                    scaleAmount(maximumExpectedShortfall)
            ));
        }

        BigDecimal concentrationThreshold = startingCash.multiply(new BigDecimal("0.30")).max(new BigDecimal("50000.00"));
        for (DailyForecastRow r : rows) {
            BigDecimal dayOut = new BigDecimal(r.totalExpectedOutflows());
            if (dayOut.compareTo(concentrationThreshold) > 0) {
                warnings.add(new ForecastLiquidityWarning(
                        "PAYABLE_CONCENTRATION",
                        "WARNING",
                        "High Outflow Concentration",
                        "Significant scheduled cash outflows of " + currency + " " + r.totalExpectedOutflows() + " concentrated on " + r.date() + ".",
                        r.date(),
                        r.totalExpectedOutflows()
                ));
            }
        }

        if (overdueReceivablesTotal.compareTo(BigDecimal.ZERO) > 0 && (scenarioReq == null || !Boolean.TRUE.equals(scenarioReq.includeOverdueReceivables()))) {
            warnings.add(new ForecastLiquidityWarning(
                    "OVERDUE_RECEIVABLES_EXPOSURE",
                    "WARNING",
                    "Overdue Receivables Risk",
                    currency + " " + scaleAmount(overdueReceivablesTotal) + " in overdue customer receivables is excluded from baseline daily cash flow.",
                    asOfDate,
                    scaleAmount(overdueReceivablesTotal)
            ));
        }

        if (missingDueDatesCount > 0) {
            warnings.add(new ForecastLiquidityWarning(
                    "MISSING_DUE_DATES",
                    "INFO",
                    "Obligations Missing Due Dates",
                    missingDueDatesCount + " open obligations totaling " + currency + " " + scaleAmount(missingDueDatesExposure) + " lack due dates and are excluded from the daily projection.",
                    null,
                    scaleAmount(missingDueDatesExposure)
            ));
        }

        if (!pendingDrafts.isEmpty()) {
            warnings.add(new ForecastLiquidityWarning(
                    "UNPOSTED_DRAFTS_PENDING",
                    "INFO",
                    "Unposted Drafts Awaiting Review",
                    pendingDrafts.size() + " unposted drafts awaiting review (Inflow: " + currency + " " + scaleAmount(draftsInflow) + ", Outflow: " + currency + " " + scaleAmount(draftsOutflow) + ").",
                    null,
                    scaleAmount(draftsInflow.add(draftsOutflow))
            ));
        }

        // 7. Completeness metadata
        List<String> omittedCategories = List.of(
                "Inventory purchases and procurement costs (COGS engine pending Developer B F08 integration)",
                "Project milestone budgets and capital expenditures (Project finance pending Developer B F05 integration)",
                "Discretionary payroll and contractor disbursements not configured in recurring rules"
        );

        DataCompleteness completenessStatus = (missingDueDatesCount > 0 || overdueReceivablesTotal.compareTo(BigDecimal.ZERO) > 0 || !pendingDrafts.isEmpty())
                ? DataCompleteness.PARTIAL : DataCompleteness.COMPLETE;

        ForecastCompleteness completeness = new ForecastCompleteness(
                completenessStatus,
                omittedCategories,
                missingDueDatesCount,
                scaleAmount(missingDueDatesExposure),
                overdueReceivablesTotal.compareTo(BigDecimal.ZERO) > 0,
                !pendingDrafts.isEmpty(),
                activeRules.size(),
                warnings
        );

        ForecastSummary summary = new ForecastSummary(
                currency,
                asOfDate,
                horizonDays,
                horizonEndDate,
                scaleAmount(startingCash),
                scaleAmount(runningCash),
                scaleAmount(minimumProjectedCash),
                minimumCashDate,
                firstDeficitDate,
                scaleAmount(maximumExpectedShortfall),
                scaleAmount(totalInflows),
                scaleAmount(totalOutflows),
                scaleAmount(netProjectedMovement),
                scaleAmount(overdueReceivablesTotal),
                scaleAmount(overduePayablesTotal),
                scaleAmount(draftsInflow),
                scaleAmount(draftsOutflow),
                pendingDrafts.size()
        );

        return new DailyCashForecastResponse(
                CONTRACT_VERSION,
                currency,
                asOfDate,
                horizonDays,
                horizonEndDate,
                summary,
                completeness,
                rows,
                "Forecasts are deterministic projections based on existing schedules and user scenario assumptions. They do not represent guaranteed cash balances, insolvency determinations, or financial actuals."
        );
    }

    private ForecastScenarioComparison buildComparison(
            DailyCashForecastResponse baseline,
            DailyCashForecastResponse scenario,
            ForecastScenarioRequest req
    ) {
        BigDecimal baseEnding = new BigDecimal(baseline.summary().endingProjectedCash());
        BigDecimal scenEnding = new BigDecimal(scenario.summary().endingProjectedCash());
        BigDecimal endingVariance = scenEnding.subtract(baseEnding);

        BigDecimal baseMin = new BigDecimal(baseline.summary().minimumProjectedCash());
        BigDecimal scenMin = new BigDecimal(scenario.summary().minimumProjectedCash());
        BigDecimal minVariance = scenMin.subtract(baseMin);

        LocalDate baseDeficit = baseline.summary().firstDeficitDate();
        LocalDate scenDeficit = scenario.summary().firstDeficitDate();

        String deficitChange;
        if (baseDeficit == null && scenDeficit == null) {
            deficitChange = "NO_DEFICIT";
        } else if (baseDeficit == null) {
            deficitChange = "NEW_DEFICIT_ON_" + scenDeficit;
        } else if (scenDeficit == null) {
            deficitChange = "DEFICIT_RESOLVED";
        } else if (scenDeficit.isBefore(baseDeficit)) {
            deficitChange = "DEFICIT_ACCELERATED_TO_" + scenDeficit;
        } else if (scenDeficit.isAfter(baseDeficit)) {
            deficitChange = "DEFICIT_DELAYED_TO_" + scenDeficit;
        } else {
            deficitChange = "UNCHANGED";
        }

        Map<String, Object> assumptions = new LinkedHashMap<>();
        assumptions.put("collectionDelayDays", req.collectionDelayDays());
        assumptions.put("collectionPercentage", req.collectionPercentage());
        assumptions.put("paymentDelayDays", req.paymentDelayDays());
        assumptions.put("includeOverdueReceivables", req.includeOverdueReceivables());
        assumptions.put("overdueSpreadDays", req.overdueSpreadDays());

        List<DailyScenarioComparisonRow> dailyComparisons = new ArrayList<>();
        int rowsCount = Math.min(baseline.dailyRows().size(), scenario.dailyRows().size());
        for (int i = 0; i < rowsCount; i++) {
            DailyForecastRow bRow = baseline.dailyRows().get(i);
            DailyForecastRow sRow = scenario.dailyRows().get(i);

            BigDecimal bClose = new BigDecimal(bRow.projectedClosingCash());
            BigDecimal sClose = new BigDecimal(sRow.projectedClosingCash());
            BigDecimal var = sClose.subtract(bClose);

            dailyComparisons.add(new DailyScenarioComparisonRow(
                    bRow.date(),
                    bRow.dayIndex(),
                    bRow.projectedClosingCash(),
                    sRow.projectedClosingCash(),
                    scaleAmount(var),
                    bRow.totalExpectedInflows(),
                    sRow.totalExpectedInflows(),
                    bRow.totalExpectedOutflows(),
                    sRow.totalExpectedOutflows()
            ));
        }

        return new ForecastScenarioComparison(
                "User Adjusted Scenario",
                assumptions,
                baseline.summary(),
                scenario.summary(),
                scaleAmount(endingVariance),
                scaleAmount(minVariance),
                deficitChange,
                dailyComparisons
        );
    }

    private BigDecimal computeAuthoritativeStartingCash(UUID businessId, String currency, LocalDate asOfDate) {
        List<FinancialAccount> accounts = accountRepository.findByBusinessId(businessId).stream()
                .filter(a -> a.getStatus() == AccountStatus.ACTIVE)
                .filter(a -> currency.equalsIgnoreCase(a.getCurrency()))
                .toList();

        if (accounts.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
        }

        List<AccountMovementSum> movementSums = movementRepository.findMovementSumsByBusinessIdAndCutoffDate(businessId, asOfDate);
        Map<UUID, Map<MovementDirection, BigDecimal>> sumMap = new HashMap<>();
        for (AccountMovementSum s : movementSums) {
            sumMap.computeIfAbsent(s.getAccountId(), k -> new EnumMap<>(MovementDirection.class))
                    .put(s.getMovementDirection(), s.getTotalAmount());
        }

        BigDecimal total = BigDecimal.ZERO;
        for (FinancialAccount acc : accounts) {
            if (acc.getOpeningDate() != null && asOfDate.isBefore(acc.getOpeningDate())) {
                continue; // Account not yet opened as of historical evaluation date
            }

            BigDecimal bal = acc.getOpeningBalance() != null ? acc.getOpeningBalance() : BigDecimal.ZERO;

            Map<MovementDirection, BigDecimal> dirMap = sumMap.get(acc.getId());
            if (dirMap != null) {
                BigDecimal increases = dirMap.getOrDefault(MovementDirection.INCREASE, BigDecimal.ZERO);
                BigDecimal decreases = dirMap.getOrDefault(MovementDirection.DECREASE, BigDecimal.ZERO);
                bal = bal.add(increases).subtract(decreases);
            }
            total = total.add(bal);
        }

        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private String sanitizeCurrency(String currencyParam) {
        if (currencyParam == null || currencyParam.isBlank()) {
            return "PKR";
        }
        String clean = currencyParam.trim().toUpperCase(Locale.ROOT);
        if (clean.length() != 3 || !clean.matches("[A-Z]{3}")) {
            throw new ForecastValidationException("currency", "Currency code must be a 3-letter ISO code (e.g. PKR, USD)");
        }
        return clean;
    }

    private int sanitizeHorizonDays(Integer horizonDaysParam) {
        if (horizonDaysParam == null) {
            return DEFAULT_HORIZON_DAYS;
        }
        if (horizonDaysParam < MIN_HORIZON_DAYS || horizonDaysParam > MAX_HORIZON_DAYS) {
            throw new ForecastValidationException("horizonDays", "Horizon must be between " + MIN_HORIZON_DAYS + " and " + MAX_HORIZON_DAYS + " days");
        }
        return horizonDaysParam;
    }

    private LocalDate parseAsOfDate(String asOfDateStr) {
        if (asOfDateStr == null || asOfDateStr.isBlank()) {
            return LocalDate.now(BUSINESS_ZONE);
        }
        try {
            return LocalDate.parse(asOfDateStr.trim());
        } catch (DateTimeParseException e) {
            throw new ForecastValidationException("asOfDate", "Invalid date format. Expected YYYY-MM-DD");
        }
    }

    private void validateScenarioParameters(ForecastScenarioRequest req) {
        if (req.collectionDelayDays() != null && (req.collectionDelayDays() < 0 || req.collectionDelayDays() > 90)) {
            throw new ForecastValidationException("collectionDelayDays", "collectionDelayDays must be between 0 and 90");
        }
        if (req.paymentDelayDays() != null && (req.paymentDelayDays() < 0 || req.paymentDelayDays() > 90)) {
            throw new ForecastValidationException("paymentDelayDays", "paymentDelayDays must be between 0 and 90");
        }
        if (req.collectionPercentage() != null) {
            if (req.collectionPercentage().compareTo(BigDecimal.ZERO) < 0 || req.collectionPercentage().compareTo(new BigDecimal("100.00")) > 0) {
                throw new ForecastValidationException("collectionPercentage", "collectionPercentage must be between 0.00 and 100.00");
            }
        }
        if (req.overdueSpreadDays() != null && (req.overdueSpreadDays() < 1 || req.overdueSpreadDays() > 30)) {
            throw new ForecastValidationException("overdueSpreadDays", "overdueSpreadDays must be between 1 and 30");
        }
    }

    private void requirePermission(BusinessAccessContext context, BusinessPermission permission) {
        if (context == null || context.businessId() == null) {
            throw new AccessDeniedException("Authenticated active business context is required");
        }
        if (!authorizationService.hasRolePermission(context.role(), permission)) {
            throw new AccessDeniedException("Role " + context.role() + " does not have permission: " + permission);
        }
    }

    private String scaleAmount(BigDecimal amount) {
        if (amount == null) return "0.00";
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
