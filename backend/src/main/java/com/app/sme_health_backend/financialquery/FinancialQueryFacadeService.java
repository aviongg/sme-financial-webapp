package com.app.sme_health_backend.financialquery;

import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.model.BalanceStatus;
import com.app.sme_health_backend.accounting.model.CurrencyRegistry;
import com.app.sme_health_backend.accounting.model.MovementDirection;
import com.app.sme_health_backend.accounting.model.TransactionEventType;
import com.app.sme_health_backend.accounting.repository.AccountMovementRepository;
import com.app.sme_health_backend.accounting.repository.AccountMovementSum;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.counterparty.Counterparty;
import com.app.sme_health_backend.counterparty.CounterpartyRepository;
import com.app.sme_health_backend.financialquery.dto.*;
import com.app.sme_health_backend.financialquery.exception.FinancialQueryValidationException;
import com.app.sme_health_backend.financialquery.model.CapabilityStatus;
import com.app.sme_health_backend.financialquery.model.DataCompleteness;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.obligation.FinancialObligation;
import com.app.sme_health_backend.obligation.FinancialObligationRepository;
import com.app.sme_health_backend.obligation.FinancialObligationService;
import com.app.sme_health_backend.obligation.PaymentPlanService;
import com.app.sme_health_backend.obligation.aging.AgingBucket;
import com.app.sme_health_backend.obligation.aging.ObligationAgingDetail;
import com.app.sme_health_backend.obligation.aging.ObligationAgingResult;
import com.app.sme_health_backend.obligation.aging.ObligationDirection;
import com.app.sme_health_backend.obligation.dto.PaymentCalendarItemResponse;
import com.app.sme_health_backend.obligation.dto.PaymentCalendarResponse;
import com.app.sme_health_backend.recurring.calculator.RecurrenceCalculator;
import com.app.sme_health_backend.recurring.entity.RecurringRule;
import com.app.sme_health_backend.recurring.entity.RecurringRuleOccurrence;
import com.app.sme_health_backend.recurring.model.RecurringOccurrenceStatus;
import com.app.sme_health_backend.recurring.model.RecurringRuleStatus;
import com.app.sme_health_backend.recurring.repository.RecurringRuleOccurrenceRepository;
import com.app.sme_health_backend.recurring.repository.RecurringRuleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class FinancialQueryFacadeService implements FinancialQueryFacade {

    public static final String CONTRACT_VERSION = "1.0";
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Karachi");
    public static final int MAX_OVERDUE_RECEIVABLES_LIMIT = 100;
    public static final int DEFAULT_OVERDUE_RECEIVABLES_LIMIT = 50;
    public static final long MAX_UPCOMING_WINDOW_DAYS = 90L;
    public static final int MAX_QUERY_STRING_LENGTH = 500;

    private final FinancialAccountRepository accountRepository;
    private final AccountMovementRepository movementRepository;
    private final FinancialObligationService obligationService;
    private final FinancialObligationRepository obligationRepository;
    private final PaymentPlanService paymentPlanService;
    private final CounterpartyRepository counterpartyRepository;
    private final RecurringRuleOccurrenceRepository recurringOccurrenceRepository;
    private final RecurringRuleRepository recurringRuleRepository;
    private final BusinessAuthorizationService authorizationService;

    @Autowired
    public FinancialQueryFacadeService(
            FinancialAccountRepository accountRepository,
            AccountMovementRepository movementRepository,
            FinancialObligationService obligationService,
            FinancialObligationRepository obligationRepository,
            PaymentPlanService paymentPlanService,
            CounterpartyRepository counterpartyRepository,
            @Autowired(required = false) RecurringRuleOccurrenceRepository recurringOccurrenceRepository,
            @Autowired(required = false) RecurringRuleRepository recurringRuleRepository,
            @Autowired(required = false) BusinessAuthorizationService authorizationService
    ) {
        this.accountRepository = accountRepository;
        this.movementRepository = movementRepository;
        this.obligationService = obligationService;
        this.obligationRepository = obligationRepository;
        this.paymentPlanService = paymentPlanService;
        this.counterpartyRepository = counterpartyRepository;
        this.recurringOccurrenceRepository = recurringOccurrenceRepository;
        this.recurringRuleRepository = recurringRuleRepository;
        this.authorizationService = authorizationService != null ? authorizationService : new BusinessAuthorizationService(null);
    }

    public FinancialQueryFacadeService(
            FinancialAccountRepository accountRepository,
            AccountMovementRepository movementRepository,
            FinancialObligationService obligationService,
            FinancialObligationRepository obligationRepository,
            PaymentPlanService paymentPlanService,
            CounterpartyRepository counterpartyRepository,
            RecurringRuleOccurrenceRepository recurringOccurrenceRepository,
            RecurringRuleRepository recurringRuleRepository
    ) {
        this(
                accountRepository,
                movementRepository,
                obligationService,
                obligationRepository,
                paymentPlanService,
                counterpartyRepository,
                recurringOccurrenceRepository,
                recurringRuleRepository,
                new BusinessAuthorizationService(null)
        );
    }

    @Override
    public CapabilitiesResponse getCapabilities(BusinessAccessContext context) {
        requirePermission(context, BusinessPermission.FINANCIAL_DATA_READ);

        List<CapabilityItem> capabilities = List.of(
                new CapabilityItem(
                        "getCashPosition",
                        CapabilityStatus.AVAILABLE,
                        "Authoritative cash & bank balances and movements across accounts",
                        null
                ),
                new CapabilityItem(
                        "getOverdueReceivables",
                        CapabilityStatus.AVAILABLE,
                        "Canonical 7-bucket overdue receivables aging and outstanding balances",
                        null
                ),
                new CapabilityItem(
                        "getUpcomingObligations",
                        CapabilityStatus.AVAILABLE,
                        "Upcoming receivables, payables, installments, and recurring operational projections",
                        null
                ),
                new CapabilityItem(
                        "getProjectFinancials",
                        CapabilityStatus.UNAVAILABLE,
                        "Project financial tracking (F05) is owned by Developer B and not yet integrated into authoritative financial services",
                        "Project finance domain (F05) is not yet integrated into FinSight authoritative services."
                ),
                new CapabilityItem(
                        "getProjectFundingGap",
                        CapabilityStatus.UNAVAILABLE,
                        "Project funding gap analysis (F07) requires an approved persisted project plan integration",
                        "Project funding gap calculations require an approved persisted project financial plan, which is currently unintegrated."
                ),
                new CapabilityItem(
                        "getMarginDrivers",
                        CapabilityStatus.UNAVAILABLE,
                        "Inventory valuation and COGS margin drivers (F08) are owned by Developer B and not yet integrated",
                        "Inventory valuation and COGS margin drivers (F08) are not yet integrated into FinSight authoritative services."
                ),
                new CapabilityItem(
                        "getFxSettlementImpact",
                        CapabilityStatus.UNAVAILABLE,
                        "Multi-currency realized FX settlement impact (F11) is not yet implemented",
                        "Multi-currency realized FX settlement impact (F11) is not yet implemented."
                )
        );

        return new CapabilitiesResponse(CONTRACT_VERSION, context.businessId(), Instant.now(), capabilities);
    }

    @Override
    public FinancialQueryEnvelope<CashPositionResult> getCashPosition(BusinessAccessContext context, CashPositionQuery query) {
        requirePermission(context, BusinessPermission.FINANCIAL_ACCOUNT_READ);

        LocalDate asOfDate = parseAsOfDate(query != null ? query.asOfDate() : null, "asOfDate");

        AccountType typeFilter = null;
        if (query != null && query.accountType() != null && !query.accountType().isBlank()) {
            try {
                typeFilter = AccountType.valueOf(query.accountType().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new FinancialQueryValidationException("accountType", "Invalid accountType: " + query.accountType());
            }
        }

        List<FinancialAccount> allAccounts = new ArrayList<>(accountRepository.findByBusinessId(context.businessId()));
        if (typeFilter != null) {
            final AccountType finalType = typeFilter;
            allAccounts = new ArrayList<>(allAccounts.stream().filter(a -> a.getAccountType() == finalType).toList());
        }

        allAccounts.sort(Comparator
                .comparing(FinancialAccount::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(FinancialAccount::getAccountName, Comparator.nullsLast(String::compareTo))
                .thenComparing(FinancialAccount::getId, Comparator.nullsLast(Comparator.naturalOrder())));

        Map<UUID, BigDecimal> netMovementsByAccount = new HashMap<>();
        List<AccountMovementSum> businessMovementSums =
                movementRepository.findMovementSumsByBusinessIdAndCutoffDate(context.businessId(), asOfDate);
        if (businessMovementSums != null) {
            for (AccountMovementSum sum : businessMovementSums) {
                if (sum == null || sum.getAccountId() == null || sum.getTotalAmount() == null) continue;
                BigDecimal current = netMovementsByAccount.getOrDefault(sum.getAccountId(), BigDecimal.ZERO);
                if (sum.getMovementDirection() == MovementDirection.INCREASE) {
                    netMovementsByAccount.put(sum.getAccountId(), current.add(sum.getTotalAmount()));
                } else if (sum.getMovementDirection() == MovementDirection.DECREASE) {
                    netMovementsByAccount.put(sum.getAccountId(), current.subtract(sum.getTotalAmount()));
                }
            }
        }

        List<AccountCashPosition> accountPositions = new ArrayList<>();
        List<FinancialEvidenceReference> evidence = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        Map<String, List<FinancialAccount>> accountsByCurrency = new TreeMap<>();
        for (FinancialAccount acc : allAccounts) {
            String currencyKey = acc.getCurrency() != null ? acc.getCurrency().trim().toUpperCase(Locale.ROOT) : "UNKNOWN";
            accountsByCurrency.computeIfAbsent(currencyKey, k -> new ArrayList<>()).add(acc);
        }

        List<CurrencyCashSummary> currencySummaries = new ArrayList<>();
        int preOpeningAccountCount = 0;
        boolean hasIntegrityWarning = false;

        for (Map.Entry<String, List<FinancialAccount>> entry : accountsByCurrency.entrySet()) {
            String currency = entry.getKey();
            List<FinancialAccount> curAccounts = entry.getValue();

            BigDecimal totalCurOpening = BigDecimal.ZERO;
            BigDecimal totalCurNetMovements = BigDecimal.ZERO;
            BigDecimal totalCurBalance = BigDecimal.ZERO;
            int curDecimals = CurrencyRegistry.findScale(currency).orElse(2);
            int validCurrencyAccounts = 0;

            for (FinancialAccount acc : curAccounts) {
                Integer declaredDecimals = acc.getCurrencyDecimals();
                int decimals = declaredDecimals != null ? declaredDecimals : curDecimals;
                curDecimals = decimals;

                if (!CurrencyRegistry.isSupported(currency, declaredDecimals)) {
                    hasIntegrityWarning = true;
                    warnings.add("Account '" + acc.getAccountName() + "' has unsupported currency metadata (" + currency + ", scale=" + declaredDecimals + ") and was excluded from currency totals.");
                    continue;
                }

                if (acc.getOpeningDate() == null) {
                    hasIntegrityWarning = true;
                    warnings.add("Account '" + acc.getAccountName() + "' balance evaluation unavailable (MISSING_OPENING_DATE).");
                    continue;
                }

                CurrencyRegistry.BalanceEvaluation evalPersistedOpening =
                        CurrencyRegistry.evaluateBalance(acc.getOpeningBalance(), currency, decimals);
                if (evalPersistedOpening.status() != BalanceStatus.AVAILABLE) {
                    hasIntegrityWarning = true;
                    warnings.add("Account '" + acc.getAccountName() + "' balance evaluation unavailable (" + evalPersistedOpening.unavailableReason() + ").");
                    continue;
                }

                BigDecimal effectiveOpeningForSummary;
                BigDecimal netMov;
                String formattedNetMov;
                String formattedCurrent;

                if (asOfDate.isBefore(acc.getOpeningDate())) {
                    effectiveOpeningForSummary = BigDecimal.ZERO;
                    netMov = BigDecimal.ZERO;
                    String canonicalZero = CurrencyRegistry.formatCanonicalZero(currency);
                    formattedNetMov = canonicalZero;
                    formattedCurrent = canonicalZero;
                    preOpeningAccountCount++;
                } else {
                    effectiveOpeningForSummary = acc.getOpeningBalance();
                    netMov = netMovementsByAccount.getOrDefault(acc.getId(), BigDecimal.ZERO);
                    BigDecimal rawCurrent = effectiveOpeningForSummary.add(netMov);

                    CurrencyRegistry.BalanceEvaluation evalNet = CurrencyRegistry.evaluateBalance(netMov, currency, decimals);
                    CurrencyRegistry.BalanceEvaluation evalCurrent = CurrencyRegistry.evaluateBalance(rawCurrent, currency, decimals);

                    if (evalNet.status() != BalanceStatus.AVAILABLE || evalCurrent.status() != BalanceStatus.AVAILABLE) {
                        hasIntegrityWarning = true;
                        String reason = evalCurrent.status() != BalanceStatus.AVAILABLE
                                ? evalCurrent.unavailableReason()
                                : evalNet.unavailableReason();
                        warnings.add("Account '" + acc.getAccountName() + "' balance evaluation unavailable (" + reason + ").");
                        continue;
                    }
                    formattedNetMov = evalNet.formattedBalance();
                    formattedCurrent = evalCurrent.formattedBalance();
                }

                BigDecimal scaledSummaryOpening = effectiveOpeningForSummary.setScale(decimals, RoundingMode.UNNECESSARY);
                BigDecimal scaledNetMov = netMov.setScale(decimals, RoundingMode.UNNECESSARY);
                BigDecimal scaledCurrent = scaledSummaryOpening.add(scaledNetMov).setScale(decimals, RoundingMode.UNNECESSARY);

                accountPositions.add(new AccountCashPosition(
                        acc.getId(),
                        acc.getAccountName(),
                        acc.getAccountType().name(),
                        currency,
                        decimals,
                        evalPersistedOpening.formattedBalance(),
                        acc.getOpeningDate(),
                        formattedNetMov,
                        formattedCurrent,
                        acc.getStatus().name(),
                        acc.getInstitutionName(),
                        CurrencyRegistry.maskIdentifier(acc.getAccountIdentifier())
                ));

                evidence.add(new FinancialEvidenceReference(
                        "FINANCIAL_ACCOUNT",
                        acc.getId().toString(),
                        "Ledger account balance: " + acc.getAccountName() + " (" + currency + ") as of " + asOfDate,
                        acc.getCreatedAt()
                ));

                totalCurOpening = totalCurOpening.add(scaledSummaryOpening);
                totalCurNetMovements = totalCurNetMovements.add(scaledNetMov);
                totalCurBalance = totalCurBalance.add(scaledCurrent);
                validCurrencyAccounts++;
            }

            if (validCurrencyAccounts > 0) {
                currencySummaries.add(new CurrencyCashSummary(
                        currency,
                        curDecimals,
                        formatScaledDecimal(totalCurOpening, curDecimals),
                        formatScaledDecimal(totalCurNetMovements, curDecimals),
                        formatScaledDecimal(totalCurBalance, curDecimals),
                        validCurrencyAccounts
                ));
            }
        }

        if (currencySummaries.size() > 1) {
            warnings.add("Multi-currency business: balances are reported strictly per currency. Cross-currency summation without an approved FX rate policy is prohibited.");
        }
        if (preOpeningAccountCount > 0) {
            warnings.add(preOpeningAccountCount + " account(s) opened after asOfDate (" + asOfDate + ") contributed zero balance as of the requested historical date.");
        }

        DataCompleteness completeness;
        if (allAccounts.isEmpty()) {
            completeness = DataCompleteness.EMPTY;
        } else if (hasIntegrityWarning) {
            completeness = DataCompleteness.PARTIAL;
        } else {
            completeness = DataCompleteness.COMPLETE;
        }

        CashPositionResult result = new CashPositionResult(asOfDate, currencySummaries, accountPositions, accountPositions.size());

        return FinancialQueryEnvelope.available(
                "getCashPosition",
                CONTRACT_VERSION,
                context.businessId(),
                asOfDate.toString(),
                completeness,
                warnings,
                evidence,
                result
        );
    }

    @Override
    public FinancialQueryEnvelope<OverdueReceivablesResult> getOverdueReceivables(BusinessAccessContext context, OverdueReceivablesQuery query) {
        requirePermission(context, BusinessPermission.FINANCIAL_TRANSACTION_READ);

        LocalDate asOfDate = parseAsOfDate(query != null ? query.asOfDate() : null, "asOfDate");

        int limit = DEFAULT_OVERDUE_RECEIVABLES_LIMIT;
        if (query != null && query.limit() != null) {
            if (query.limit() < 1 || query.limit() > MAX_OVERDUE_RECEIVABLES_LIMIT) {
                throw new FinancialQueryValidationException("limit", "Query limit must be between 1 and " + MAX_OVERDUE_RECEIVABLES_LIMIT);
            }
            limit = query.limit();
        }

        int offset = 0;
        if (query != null && query.offset() != null) {
            if (query.offset() < 0) {
                throw new FinancialQueryValidationException("offset", "Query offset must be greater than or equal to 0");
            }
            offset = query.offset();
        }

        ObligationAgingResult agingResult = obligationService.getAging(context, asOfDate.toString());

        List<ObligationAgingDetail> overdueRecDetails = agingResult.details().stream()
                .filter(d -> d.direction() == ObligationDirection.RECEIVABLE)
                .filter(d -> d.isOverdue() && d.asOfOutstanding().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator
                        .comparing((ObligationAgingDetail d) -> d.daysOverdue() != null ? d.daysOverdue() : 0, Comparator.reverseOrder())
                        .thenComparing(ObligationAgingDetail::asOfOutstanding, Comparator.reverseOrder())
                        .thenComparing(ObligationAgingDetail::obligationId))
                .toList();

        int endIndex = Math.min(overdueRecDetails.size(), offset + limit);
        List<ObligationAgingDetail> pagedDetails = offset >= overdueRecDetails.size()
                ? Collections.emptyList()
                : overdueRecDetails.subList(offset, endIndex);

        Set<UUID> obIds = pagedDetails.stream()
                .map(d -> UUID.fromString(d.obligationId()))
                .collect(Collectors.toSet());

        Map<UUID, FinancialObligation> obligationMap = obIds.isEmpty() ? Collections.emptyMap() :
                obligationRepository.findAllById(obIds).stream()
                        .filter(o -> o.getBusinessId().equals(context.businessId()))
                        .collect(Collectors.toMap(FinancialObligation::getId, o -> o));

        Set<UUID> cpIds = obligationMap.values().stream()
                .map(FinancialObligation::getCounterpartyId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, Counterparty> cpMap = cpIds.isEmpty() ? Collections.emptyMap() :
                counterpartyRepository.findAllById(cpIds).stream()
                        .filter(c -> c.getBusinessId().equals(context.businessId()))
                        .collect(Collectors.toMap(Counterparty::getId, c -> c));

        List<OverdueReceivableItem> items = new ArrayList<>();
        List<FinancialEvidenceReference> evidence = new ArrayList<>();

        for (ObligationAgingDetail d : pagedDetails) {
            UUID obId = UUID.fromString(d.obligationId());
            FinancialObligation ob = obligationMap.get(obId);
            Counterparty cp = ob != null && ob.getCounterpartyId() != null ? cpMap.get(ob.getCounterpartyId()) : null;

            BigDecimal settled = d.originalAmount().subtract(d.asOfOutstanding());

            items.add(new OverdueReceivableItem(
                    obId,
                    ob != null ? ob.getReferenceNumber() : null,
                    ob != null ? ob.getCounterpartyId() : null,
                    cp != null ? cp.getDisplayName() : "Unknown Counterparty",
                    d.issueDate(),
                    d.dueDate(),
                    d.daysOverdue() != null ? d.daysOverdue() : 0,
                    d.assignedBucket() != null ? d.assignedBucket().name() : null,
                    formatScaledDecimal(d.originalAmount(), 2),
                    formatScaledDecimal(settled, 2),
                    formatScaledDecimal(d.asOfOutstanding(), 2)
            ));

            String refDesc = ob != null && ob.getReferenceNumber() != null ? ob.getReferenceNumber() : d.obligationId();
            evidence.add(new FinancialEvidenceReference(
                    "FINANCIAL_OBLIGATION",
                    d.obligationId(),
                    "Receivable obligation: " + refDesc + " (Overdue: " + d.daysOverdue() + " days)",
                    ob != null ? ob.getCreatedAt() : null
            ));
        }

        // Aggregate by canonical 7 aging buckets
        List<AgingBucketSummary> bucketSummaries = new ArrayList<>();
        if (agingResult.receivables() != null && agingResult.receivables().buckets() != null) {
            for (AgingBucket b : AgingBucket.values()) {
                com.app.sme_health_backend.obligation.aging.BucketSummary bs = agingResult.receivables().getBucket(b);
                bucketSummaries.add(new AgingBucketSummary(
                        b.name(),
                        formatBucketLabel(b),
                        formatScaledDecimal(bs.totalOutstanding(), 2),
                        bs.count()
                ));
            }
        }

        BigDecimal totalOverdue = agingResult.receivableOverdueOutstanding();
        boolean isWindowed = !overdueRecDetails.isEmpty() && (offset > 0 || endIndex < overdueRecDetails.size());
        DataCompleteness completeness = overdueRecDetails.isEmpty() ? DataCompleteness.EMPTY :
                (isWindowed ? DataCompleteness.PARTIAL : DataCompleteness.COMPLETE);

        List<String> warnings = new ArrayList<>();
        if (isWindowed) {
            warnings.add("Pagination window (offset=" + offset + ", limit=" + limit + ") returned " + items.size()
                    + " of " + overdueRecDetails.size() + " total overdue receivable obligations.");
        }

        OverdueReceivablesResult result = new OverdueReceivablesResult(
                asOfDate,
                "PKR",
                formatScaledDecimal(totalOverdue, 2),
                overdueRecDetails.size(),
                bucketSummaries,
                items
        );

        return FinancialQueryEnvelope.available(
                "getOverdueReceivables",
                CONTRACT_VERSION,
                context.businessId(),
                asOfDate.toString(),
                completeness,
                warnings,
                evidence,
                result
        );
    }

    @Override
    public FinancialQueryEnvelope<UpcomingObligationsResult> getUpcomingObligations(BusinessAccessContext context, UpcomingObligationsQuery query) {
        requirePermission(context, BusinessPermission.FINANCIAL_TRANSACTION_READ);

        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        LocalDate startDate = today;
        LocalDate endDate = today.plusDays(30);

        if (query != null && query.fromDate() != null && !query.fromDate().isBlank()) {
            startDate = parseAsOfDate(query.fromDate(), "fromDate");
        }
        if (query != null && query.toDate() != null && !query.toDate().isBlank()) {
            endDate = parseAsOfDate(query.toDate(), "toDate");
        }

        if (endDate.isBefore(startDate)) {
            throw new FinancialQueryValidationException("toDate", "toDate cannot precede fromDate");
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) > MAX_UPCOMING_WINDOW_DAYS) {
            throw new FinancialQueryValidationException("toDate", "Upcoming obligation query window cannot exceed " + MAX_UPCOMING_WINDOW_DAYS + " days");
        }

        String directionFilter = null;
        if (query != null && query.direction() != null && !query.direction().isBlank() && !"ALL".equalsIgnoreCase(query.direction().trim())) {
            try {
                ObligationDirection.valueOf(query.direction().trim().toUpperCase(Locale.ROOT));
                directionFilter = query.direction().trim().toUpperCase(Locale.ROOT);
            } catch (IllegalArgumentException e) {
                throw new FinancialQueryValidationException("direction", "Invalid direction: " + query.direction() + ". Expected RECEIVABLE, PAYABLE or ALL");
            }
        }

        PaymentCalendarResponse calendar = paymentPlanService.getPaymentCalendar(
                context, directionFilter, startDate.toString(), endDate.toString()
        );

        Set<UUID> calendarObIds = calendar.items().stream()
                .map(PaymentCalendarItemResponse::obligationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, FinancialObligation> calendarObligationMap = calendarObIds.isEmpty() ? Collections.emptyMap() :
                obligationRepository.findAllById(calendarObIds).stream()
                        .filter(o -> o.getBusinessId().equals(context.businessId()))
                        .collect(Collectors.toMap(FinancialObligation::getId, o -> o));

        List<UpcomingObligationItem> items = new ArrayList<>();
        List<FinancialEvidenceReference> evidence = new ArrayList<>();

        for (PaymentCalendarItemResponse item : calendar.items()) {
            items.add(new UpcomingObligationItem(
                    item.obligationId(),
                    item.direction(),
                    item.counterpartyId(),
                    item.counterpartyName(),
                    item.referenceNumber(),
                    item.sourceType(),
                    item.itemType(),
                    item.date(),
                    item.amount(),
                    item.settledAmount(),
                    item.outstandingAmount(),
                    item.isOverdue(),
                    item.notes()
            ));

            FinancialObligation ob = calendarObligationMap.get(item.obligationId());
            String evidenceType = "SCHEDULED_INSTALLMENT".equals(item.itemType())
                    ? "OBLIGATION_PAYMENT_SCHEDULE"
                    : "FINANCIAL_OBLIGATION";
            evidence.add(new FinancialEvidenceReference(
                    evidenceType,
                    item.obligationId().toString(),
                    item.direction() + " " + item.itemType() + ": " + (item.referenceNumber() != null ? item.referenceNumber() : item.obligationId())
                            + " due " + item.date() + " (PKR " + item.amount() + ")",
                    ob != null ? ob.getCreatedAt() : null
            ));
        }

        // Query recurring operations strictly as non-binding projected estimates (NEVER recognized liabilities)
        List<RecurringProjectionItem> recurringProjections = new ArrayList<>();
        collectRecurringProjections(
                context.businessId(),
                startDate,
                endDate,
                directionFilter,
                recurringProjections,
                evidence
        );

        List<String> warnings = new ArrayList<>();
        warnings.add("Recurring operational projections are non-binding estimates and are NOT legally recognized receivables or payables.");

        DataCompleteness completeness = items.isEmpty() && recurringProjections.isEmpty()
                ? DataCompleteness.EMPTY
                : DataCompleteness.COMPLETE;

        UpcomingObligationsResult result = new UpcomingObligationsResult(
                startDate,
                endDate,
                "PKR",
                calendar.summary().totalExpectedReceivable(),
                calendar.summary().totalExpectedPayable(),
                calendar.summary().overdueReceivable(),
                calendar.summary().overduePayable(),
                items,
                recurringProjections
        );

        return FinancialQueryEnvelope.available(
                "getUpcomingObligations",
                CONTRACT_VERSION,
                context.businessId(),
                startDate + ".." + endDate,
                completeness,
                warnings,
                evidence,
                result
        );
    }

    @Override
    public FinancialQueryEnvelope<Void> getProjectFinancials(BusinessAccessContext context, UUID projectId) {
        requirePermission(context, BusinessPermission.FINANCIAL_DATA_READ);

        return FinancialQueryEnvelope.unavailable(
                "getProjectFinancials",
                CONTRACT_VERSION,
                context.businessId(),
                "Project finance domain (F05) is owned by Developer B and has not yet been integrated into authoritative financial services."
        );
    }

    @Override
    public FinancialQueryEnvelope<Void> getProjectFundingGap(BusinessAccessContext context, UUID projectId) {
        requirePermission(context, BusinessPermission.FINANCIAL_DATA_READ);

        return FinancialQueryEnvelope.unavailable(
                "getProjectFundingGap",
                CONTRACT_VERSION,
                context.businessId(),
                "Project funding gap analysis (F07) requires an approved persisted project financial plan, which is currently unintegrated."
        );
    }

    @Override
    public FinancialQueryEnvelope<Void> getMarginDrivers(BusinessAccessContext context, String query) {
        requirePermission(context, BusinessPermission.FINANCIAL_DATA_READ);
        validateOptionalQueryParam(query);

        return FinancialQueryEnvelope.unavailable(
                "getMarginDrivers",
                CONTRACT_VERSION,
                context.businessId(),
                "Inventory valuation and COGS margin drivers (F08) are owned by Developer B and have not yet been integrated."
        );
    }

    @Override
    public FinancialQueryEnvelope<Void> getFxSettlementImpact(BusinessAccessContext context, String query) {
        requirePermission(context, BusinessPermission.FINANCIAL_DATA_READ);
        validateOptionalQueryParam(query);

        return FinancialQueryEnvelope.unavailable(
                "getFxSettlementImpact",
                CONTRACT_VERSION,
                context.businessId(),
                "Multi-currency realized FX settlement impact (F11) is not yet implemented."
        );
    }

    private void collectRecurringProjections(
            UUID businessId,
            LocalDate startDate,
            LocalDate endDate,
            String directionFilter,
            List<RecurringProjectionItem> recurringProjections,
            List<FinancialEvidenceReference> evidence
    ) {
        if (recurringOccurrenceRepository == null || recurringRuleRepository == null) {
            return;
        }

        List<RecurringRuleOccurrence> candidateOccurrences = new ArrayList<>();
        Page<RecurringRuleOccurrence> scheduledPage = recurringOccurrenceRepository.findByBusinessIdAndStatus(
                businessId, RecurringOccurrenceStatus.SCHEDULED, PageRequest.of(0, 200)
        );
        if (scheduledPage != null) {
            candidateOccurrences.addAll(scheduledPage.getContent());
        }
        Page<RecurringRuleOccurrence> draftReadyPage = recurringOccurrenceRepository.findByBusinessIdAndStatus(
                businessId, RecurringOccurrenceStatus.DRAFT_READY, PageRequest.of(0, 200)
        );
        if (draftReadyPage != null) {
            candidateOccurrences.addAll(draftReadyPage.getContent());
        }

        // Fallback compatibility if a caller/mock only populated findByBusinessId
        if (candidateOccurrences.isEmpty()) {
            Page<RecurringRuleOccurrence> allPage = recurringOccurrenceRepository.findByBusinessId(
                    businessId, PageRequest.of(0, 200)
            );
            if (allPage != null) {
                for (RecurringRuleOccurrence occ : allPage.getContent()) {
                    if (occ.getStatus() == RecurringOccurrenceStatus.SCHEDULED
                            || occ.getStatus() == RecurringOccurrenceStatus.DRAFT_READY) {
                        candidateOccurrences.add(occ);
                    }
                }
            }
        }

        List<RecurringRuleOccurrence> inWindowOccurrences = candidateOccurrences.stream()
                .filter(o -> o.getScheduledDate() != null
                        && !o.getScheduledDate().isBefore(startDate)
                        && !o.getScheduledDate().isAfter(endDate))
                .sorted(Comparator.comparing(RecurringRuleOccurrence::getScheduledDate)
                        .thenComparing(o -> o.getId() != null ? o.getId().toString() : ""))
                .toList();

        List<RecurringRule> activeRules = new ArrayList<>();
        Page<RecurringRule> activeRulesPage = recurringRuleRepository.findByBusinessIdAndStatus(
                businessId, RecurringRuleStatus.ACTIVE, PageRequest.of(0, 200)
        );
        if (activeRulesPage != null) {
            activeRules.addAll(activeRulesPage.getContent());
        }

        Set<UUID> ruleIds = inWindowOccurrences.stream()
                .map(RecurringRuleOccurrence::getRecurringRuleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, RecurringRule> ruleMap = new HashMap<>();
        for (RecurringRule r : activeRules) {
            if (r.getId() != null && businessId.equals(r.getBusinessId())) {
                ruleMap.put(r.getId(), r);
            }
        }
        Set<UUID> missingRuleIds = ruleIds.stream().filter(id -> !ruleMap.containsKey(id)).collect(Collectors.toSet());
        if (!missingRuleIds.isEmpty()) {
            for (RecurringRule r : recurringRuleRepository.findAllById(missingRuleIds)) {
                if (r.getId() != null && businessId.equals(r.getBusinessId())) {
                    ruleMap.put(r.getId(), r);
                }
            }
        }

        Set<UUID> accIds = ruleMap.values().stream()
                .map(RecurringRule::getAccountId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, FinancialAccount> accMap = accIds.isEmpty() ? Collections.emptyMap() :
                accountRepository.findAllById(accIds).stream()
                        .filter(a -> businessId.equals(a.getBusinessId()))
                        .collect(Collectors.toMap(FinancialAccount::getId, a -> a));

        Set<String> seenRuleDates = new HashSet<>();

        // 1. Existing unposted occurrences in window
        for (RecurringRuleOccurrence occ : inWindowOccurrences) {
            RecurringRule rule = ruleMap.get(occ.getRecurringRuleId());
            if (rule == null) continue;

            String ruleDateKey = rule.getId() + ":" + occ.getScheduledDate();
            if (!seenRuleDates.add(ruleDateKey)) {
                continue;
            }

            String dir = TransactionEventType.DIRECT_SALE_RECEIPT == rule.getEventType() ? "RECEIVABLE" : "PAYABLE";
            if (directionFilter != null && !directionFilter.equalsIgnoreCase(dir)) {
                continue;
            }

            FinancialAccount acc = accMap.get(rule.getAccountId());

            recurringProjections.add(new RecurringProjectionItem(
                    rule.getId(),
                    rule.getName(),
                    rule.getEventType().name(),
                    dir,
                    occ.getScheduledDate(),
                    formatScaledDecimal(occ.getAmount(), 2),
                    "PKR",
                    acc != null ? acc.getAccountName() : "Account",
                    true
            ));

            evidence.add(new FinancialEvidenceReference(
                    "RECURRING_RULE_OCCURRENCE",
                    occ.getId().toString(),
                    "Recurring projection: " + rule.getName() + " due " + occ.getScheduledDate(),
                    occ.getCreatedAt()
            ));
        }

        // 2. Projected schedule dates from active V29 recurring rules whose future occurrences are not yet materialized
        for (RecurringRule rule : activeRules) {
            if (rule.getId() == null || rule.getStatus() != RecurringRuleStatus.ACTIVE) continue;
            if (rule.getNextDueDate() == null || rule.getFrequency() == null) continue;
            if (rule.getNextDueDate().isAfter(endDate)) continue;

            String dir = TransactionEventType.DIRECT_SALE_RECEIPT == rule.getEventType() ? "RECEIVABLE" : "PAYABLE";
            if (directionFilter != null && !directionFilter.equalsIgnoreCase(dir)) {
                continue;
            }

            List<LocalDate> scheduleDates = RecurrenceCalculator.previewSchedule(
                    rule.getNextDueDate(),
                    rule.getEndDate() != null && rule.getEndDate().isBefore(endDate) ? rule.getEndDate() : endDate,
                    rule.getFrequency(),
                    rule.getAnchorDayOfMonth(),
                    rule.getAnchorMonth(),
                    100
            );

            FinancialAccount acc = accMap.get(rule.getAccountId());
            for (LocalDate projectedDate : scheduleDates) {
                if (projectedDate.isBefore(startDate) || projectedDate.isAfter(endDate)) {
                    continue;
                }
                String ruleDateKey = rule.getId() + ":" + projectedDate;
                if (!seenRuleDates.add(ruleDateKey)) {
                    continue;
                }

                recurringProjections.add(new RecurringProjectionItem(
                        rule.getId(),
                        rule.getName(),
                        rule.getEventType().name(),
                        dir,
                        projectedDate,
                        formatScaledDecimal(rule.getAmount(), 2),
                        "PKR",
                        acc != null ? acc.getAccountName() : "Account",
                        true
                ));

                evidence.add(new FinancialEvidenceReference(
                        "RECURRING_RULE",
                        rule.getId().toString(),
                        "Recurring rule template projection: " + rule.getName() + " expected " + projectedDate,
                        rule.getCreatedAt()
                ));
            }
        }

        recurringProjections.sort(Comparator
                .comparing(RecurringProjectionItem::expectedDate)
                .thenComparing(RecurringProjectionItem::ruleName, Comparator.nullsLast(String::compareTo))
                .thenComparing(RecurringProjectionItem::ruleId, Comparator.nullsLast(Comparator.naturalOrder())));
    }

    private void requirePermission(BusinessAccessContext context, BusinessPermission permission) {
        if (context == null || context.businessId() == null) {
            throw new AccessDeniedException("Authenticated active business context is required");
        }
        if (!authorizationService.hasRolePermission(context.role(), permission)) {
            throw new AccessDeniedException("Role " + context.role() + " does not have permission: " + permission);
        }
    }

    private void validateOptionalQueryParam(String query) {
        if (query != null && query.length() > MAX_QUERY_STRING_LENGTH) {
            throw new FinancialQueryValidationException("query", "Query parameter must not exceed " + MAX_QUERY_STRING_LENGTH + " characters");
        }
    }

    private LocalDate parseAsOfDate(String dateStr, String fieldName) {
        if (dateStr == null || dateStr.isBlank()) {
            return LocalDate.now(BUSINESS_ZONE);
        }
        try {
            return LocalDate.parse(dateStr.trim());
        } catch (DateTimeParseException e) {
            throw new FinancialQueryValidationException(fieldName, "Invalid date format. Expected YYYY-MM-DD");
        }
    }

    private String formatScaledDecimal(BigDecimal amount, int decimals) {
        BigDecimal scaled = amount.setScale(decimals, RoundingMode.UNNECESSARY);
        if (scaled.signum() == 0) {
            return BigDecimal.ZERO.setScale(decimals, RoundingMode.UNNECESSARY).toPlainString();
        }
        return scaled.toPlainString();
    }

    private String formatBucketLabel(AgingBucket bucket) {
        if (bucket == null) {
            return "Unknown";
        }
        return switch (bucket) {
            case UNKNOWN_DUE_DATE -> "Unknown Due Date";
            case NOT_YET_DUE -> "Not Yet Due";
            case DUE_TODAY -> "Due Today";
            case OVERDUE_1_30 -> "1 - 30 Days Overdue";
            case OVERDUE_31_60 -> "31 - 60 Days Overdue";
            case OVERDUE_61_90 -> "61 - 90 Days Overdue";
            case OVERDUE_91_PLUS -> "91+ Days Overdue";
        };
    }
}
