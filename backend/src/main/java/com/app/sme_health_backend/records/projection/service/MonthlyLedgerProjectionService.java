package com.app.sme_health_backend.records.projection.service;

import com.app.sme_health_backend.accounting.draft.DraftStatus;
import com.app.sme_health_backend.accounting.draft.RecordDraft;
import com.app.sme_health_backend.accounting.draft.RecordDraftRepository;
import com.app.sme_health_backend.accounting.entity.AccountMovement;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.entity.FinancialTransaction;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.model.BalanceStatus;
import com.app.sme_health_backend.accounting.model.CurrencyRegistry;
import com.app.sme_health_backend.accounting.model.MovementDirection;
import com.app.sme_health_backend.accounting.model.TransactionEventType;
import com.app.sme_health_backend.accounting.repository.AccountMovementRepository;
import com.app.sme_health_backend.accounting.repository.AccountMovementSum;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.accounting.repository.FinancialTransactionRepository;
import com.app.sme_health_backend.financialquery.dto.FinancialEvidenceReference;
import com.app.sme_health_backend.financialquery.exception.FinancialQueryValidationException;
import com.app.sme_health_backend.financialquery.model.CapabilityStatus;
import com.app.sme_health_backend.financialquery.model.DataCompleteness;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.obligation.AdvanceStatus;
import com.app.sme_health_backend.obligation.CounterpartyAdvance;
import com.app.sme_health_backend.obligation.CounterpartyAdvanceRepository;
import com.app.sme_health_backend.obligation.FinancialObligation;
import com.app.sme_health_backend.obligation.FinancialObligationRepository;
import com.app.sme_health_backend.obligation.FinancialObligationService;
import com.app.sme_health_backend.obligation.ObligationPaymentAllocation;
import com.app.sme_health_backend.obligation.ObligationPaymentAllocationRepository;
import com.app.sme_health_backend.obligation.ObligationSourceType;
import com.app.sme_health_backend.obligation.ObligationStatus;
import com.app.sme_health_backend.obligation.aging.ObligationAgingResult;
import com.app.sme_health_backend.obligation.aging.ObligationDirection;
import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.records.projection.dto.*;
import com.app.sme_health_backend.records.projection.model.ComparisonMetricStatus;
import com.app.sme_health_backend.records.projection.model.RecognitionBasis;
import com.app.sme_health_backend.records.repository.MonthlyRecordRepository;
import com.app.sme_health_backend.recurring.entity.RecurringRuleOccurrence;
import com.app.sme_health_backend.recurring.model.RecurringOccurrenceStatus;
import com.app.sme_health_backend.recurring.repository.RecurringRuleOccurrenceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Deterministic read-only monthly financial projection service derived from FinSight's canonical ledger events.
 * Implements the financial and architectural foundation for C02 (Monthly Ledger Cutover Phase A).
 */
@Service
public class MonthlyLedgerProjectionService {

    public static final String CONTRACT_VERSION = "1.0";
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Karachi");

    private final FinancialAccountRepository accountRepository;
    private final FinancialTransactionRepository transactionRepository;
    private final AccountMovementRepository movementRepository;
    private final FinancialObligationService obligationService;
    private final FinancialObligationRepository obligationRepository;
    private final ObligationPaymentAllocationRepository allocationRepository;
    private final CounterpartyAdvanceRepository advanceRepository;
    private final MonthlyRecordRepository monthlyRecordRepository;
    private final BusinessAuthorizationService authorizationService;
    private final RecordDraftRepository recordDraftRepository;
    private final RecurringRuleOccurrenceRepository recurringOccurrenceRepository;

    @Autowired
    public MonthlyLedgerProjectionService(
            FinancialAccountRepository accountRepository,
            FinancialTransactionRepository transactionRepository,
            AccountMovementRepository movementRepository,
            FinancialObligationService obligationService,
            FinancialObligationRepository obligationRepository,
            ObligationPaymentAllocationRepository allocationRepository,
            CounterpartyAdvanceRepository advanceRepository,
            MonthlyRecordRepository monthlyRecordRepository,
            BusinessAuthorizationService authorizationService,
            @Autowired(required = false) RecordDraftRepository recordDraftRepository,
            @Autowired(required = false) RecurringRuleOccurrenceRepository recurringOccurrenceRepository
    ) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.movementRepository = movementRepository;
        this.obligationService = obligationService;
        this.obligationRepository = obligationRepository;
        this.allocationRepository = allocationRepository;
        this.advanceRepository = advanceRepository;
        this.monthlyRecordRepository = monthlyRecordRepository;
        this.authorizationService = authorizationService;
        this.recordDraftRepository = recordDraftRepository;
        this.recurringOccurrenceRepository = recurringOccurrenceRepository;
    }

    /**
     * Projects monthly financial metrics from posted canonical transactions, movements, and obligations.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public MonthlyLedgerProjectionResponse projectMonthlyLedger(
            BusinessAccessContext context,
            String monthStr,
            String asOfDateStr
    ) {
        requireReadPermission(context);

        YearMonth ym = parseYearMonth(monthStr);
        LocalDate startDate = ym.atDay(1);
        LocalDate endDate = ym.atEndOfMonth();
        LocalDate asOfDate = parseOptionalAsOfDate(asOfDateStr, endDate);

        List<FinancialEvidenceReference> evidence = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        boolean isPartial = false;

        // 1. Account Cash Positions & Currency Summaries
        List<FinancialAccount> accounts = new ArrayList<>(accountRepository.findByBusinessId(context.businessId()));
        accounts.sort(Comparator
                .comparing(FinancialAccount::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(FinancialAccount::getAccountName, Comparator.nullsLast(String::compareTo))
                .thenComparing(FinancialAccount::getId, Comparator.nullsLast(Comparator.naturalOrder())));

        LocalDate priorDay = startDate.minusDays(1);
        Map<UUID, BigDecimal> movementsUpToPriorDay = fetchNetMovementsUpToDate(context.businessId(), priorDay);
        Map<UUID, BigDecimal> movementsInMonth = fetchNetMovementsBetweenDates(context.businessId(), startDate, endDate);
        Map<UUID, BigDecimal> movementsUpToEndDate = fetchNetMovementsUpToDate(context.businessId(), endDate);

        Map<String, List<FinancialAccount>> accountsByCurrency = new TreeMap<>();
        for (FinancialAccount acc : accounts) {
            String cKey = acc.getCurrency() != null ? acc.getCurrency().trim().toUpperCase(Locale.ROOT) : "UNKNOWN";
            accountsByCurrency.computeIfAbsent(cKey, k -> new ArrayList<>()).add(acc);
        }

        List<AccountMonthlyCashPosition> accountPositions = new ArrayList<>();
        List<CurrencyMonthlyCashSummary> currencySummaries = new ArrayList<>();

        for (Map.Entry<String, List<FinancialAccount>> entry : accountsByCurrency.entrySet()) {
            String currency = entry.getKey();
            List<FinancialAccount> curAccounts = entry.getValue();
            int curDecimals = CurrencyRegistry.findScale(currency).orElse(2);

            BigDecimal totalCurOpening = BigDecimal.ZERO;
            BigDecimal totalCurStartingAdj = BigDecimal.ZERO;
            BigDecimal totalCurInflows = BigDecimal.ZERO;
            BigDecimal totalCurOutflows = BigDecimal.ZERO;
            BigDecimal totalCurNet = BigDecimal.ZERO;
            BigDecimal totalCurClosing = BigDecimal.ZERO;
            int validAccountCount = 0;

            for (FinancialAccount acc : curAccounts) {
                Integer declaredDecimals = acc.getCurrencyDecimals();
                int decimals = declaredDecimals != null ? declaredDecimals : curDecimals;

                if (!CurrencyRegistry.isSupported(currency, declaredDecimals)) {
                    isPartial = true;
                    warnings.add("Account '" + acc.getAccountName() + "' has unsupported currency (" + currency + ") and was excluded from cash totals.");
                    continue;
                }
                if (acc.getOpeningDate() == null || acc.getOpeningBalance() == null) {
                    isPartial = true;
                    warnings.add("Account '" + acc.getAccountName() + "' has missing opening balance/date metadata and was excluded from cash totals.");
                    continue;
                }

                CurrencyRegistry.BalanceEvaluation evalOpeningMeta =
                        CurrencyRegistry.evaluateBalance(acc.getOpeningBalance(), currency, decimals);
                if (evalOpeningMeta.status() != BalanceStatus.AVAILABLE) {
                    isPartial = true;
                    warnings.add("Account '" + acc.getAccountName() + "' opening balance invalid (" + evalOpeningMeta.unavailableReason() + ").");
                    continue;
                }

                BigDecimal openingAsOfStartOfMonth;
                BigDecimal startingBalanceAdjustment;

                if (acc.getOpeningDate().isAfter(endDate)) {
                    // Account not yet opened in this month
                    openingAsOfStartOfMonth = BigDecimal.ZERO;
                    startingBalanceAdjustment = BigDecimal.ZERO;
                } else if (acc.getOpeningDate().isAfter(priorDay)) {
                    // Account opened during this month
                    openingAsOfStartOfMonth = BigDecimal.ZERO;
                    startingBalanceAdjustment = acc.getOpeningBalance();
                } else {
                    // Account opened on or before priorDay
                    BigDecimal movPrior = movementsUpToPriorDay.getOrDefault(acc.getId(), BigDecimal.ZERO);
                    openingAsOfStartOfMonth = acc.getOpeningBalance().add(movPrior);
                    startingBalanceAdjustment = BigDecimal.ZERO;
                }

                BigDecimal netInMonth = movementsInMonth.getOrDefault(acc.getId(), BigDecimal.ZERO);
                BigDecimal closingAsOfEnd;
                if (acc.getOpeningDate().isAfter(endDate)) {
                    closingAsOfEnd = BigDecimal.ZERO;
                } else {
                    BigDecimal movUpToEnd = movementsUpToEndDate.getOrDefault(acc.getId(), BigDecimal.ZERO);
                    closingAsOfEnd = acc.getOpeningBalance().add(movUpToEnd);
                }

                // Granular inflows and outflows for account
                BigDecimal accInflows = fetchAccountDirectionalTotal(context.businessId(), acc.getId(), MovementDirection.INCREASE, startDate, endDate);
                BigDecimal accOutflows = fetchAccountDirectionalTotal(context.businessId(), acc.getId(), MovementDirection.DECREASE, startDate, endDate);

                accountPositions.add(new AccountMonthlyCashPosition(
                        acc.getId(),
                        acc.getAccountName(),
                        acc.getAccountType().name(),
                        currency,
                        decimals,
                        formatAmount(openingAsOfStartOfMonth, decimals),
                        formatAmount(startingBalanceAdjustment, decimals),
                        formatAmount(accInflows, decimals),
                        formatAmount(accOutflows, decimals),
                        formatAmount(netInMonth, decimals),
                        formatAmount(closingAsOfEnd, decimals),
                        acc.getStatus().name(),
                        acc.getInstitutionName(),
                        CurrencyRegistry.maskIdentifier(acc.getAccountIdentifier())
                ));

                evidence.add(new FinancialEvidenceReference(
                        "FINANCIAL_ACCOUNT",
                        acc.getId() != null ? acc.getId().toString() : "UNKNOWN",
                        "Account " + acc.getAccountName() + " (" + currency + ") monthly balance: " + formatAmount(closingAsOfEnd, decimals),
                        acc.getCreatedAt()
                ));

                totalCurOpening = totalCurOpening.add(openingAsOfStartOfMonth);
                totalCurStartingAdj = totalCurStartingAdj.add(startingBalanceAdjustment);
                totalCurInflows = totalCurInflows.add(accInflows);
                totalCurOutflows = totalCurOutflows.add(accOutflows);
                totalCurNet = totalCurNet.add(netInMonth);
                totalCurClosing = totalCurClosing.add(closingAsOfEnd);
                validAccountCount++;
            }

            if (validAccountCount > 0) {
                currencySummaries.add(new CurrencyMonthlyCashSummary(
                        currency,
                        curDecimals,
                        formatAmount(totalCurOpening, curDecimals),
                        formatAmount(totalCurStartingAdj, curDecimals),
                        formatAmount(totalCurInflows, curDecimals),
                        formatAmount(totalCurOutflows, curDecimals),
                        formatAmount(totalCurNet, curDecimals),
                        formatAmount(totalCurClosing, curDecimals),
                        validAccountCount
                ));
            }
        }

        // 2. Canonical Monthly Operating Cash Flow (Transactions in month)
        List<FinancialTransaction> transactions = transactionRepository.findByBusinessIdAndDateRange(
                context.businessId(), startDate, endDate
        );

        BigDecimal grossDirectSales = BigDecimal.ZERO;
        BigDecimal revDirectSales = BigDecimal.ZERO;
        BigDecimal grossRecCollections = BigDecimal.ZERO;
        BigDecimal revRecCollections = BigDecimal.ZERO;

        BigDecimal grossOpexPayments = BigDecimal.ZERO;
        BigDecimal revOpexPayments = BigDecimal.ZERO;
        BigDecimal grossPaySettlements = BigDecimal.ZERO;
        BigDecimal revPaySettlements = BigDecimal.ZERO;

        BigDecimal transfersIn = BigDecimal.ZERO;
        BigDecimal transfersOut = BigDecimal.ZERO;

        BigDecimal unclassifiedInflows = BigDecimal.ZERO;
        BigDecimal unclassifiedOutflows = BigDecimal.ZERO;

        for (FinancialTransaction tx : transactions) {
            BigDecimal amt = tx.getAmount() != null ? tx.getAmount() : BigDecimal.ZERO;
            boolean isReversal = tx.getReversedTransactionId() != null;

            if (tx.getEventType() == null) {
                if (tx.getMovementDirection() == MovementDirection.INCREASE) {
                    unclassifiedInflows = unclassifiedInflows.add(amt);
                } else if (tx.getMovementDirection() == MovementDirection.DECREASE) {
                    unclassifiedOutflows = unclassifiedOutflows.add(amt);
                }
            } else {
                switch (tx.getEventType()) {
                    case DIRECT_SALE_RECEIPT -> {
                        if (isReversal) revDirectSales = revDirectSales.add(amt);
                        else grossDirectSales = grossDirectSales.add(amt);
                    }
                    case RECEIVABLE_COLLECTION -> {
                        if (isReversal) revRecCollections = revRecCollections.add(amt);
                        else grossRecCollections = grossRecCollections.add(amt);
                    }
                    case OPERATING_EXPENSE_PAYMENT -> {
                        if (isReversal) revOpexPayments = revOpexPayments.add(amt);
                        else grossOpexPayments = grossOpexPayments.add(amt);
                    }
                    case PAYABLE_SETTLEMENT -> {
                        if (isReversal) revPaySettlements = revPaySettlements.add(amt);
                        else grossPaySettlements = grossPaySettlements.add(amt);
                    }
                    case INTERNAL_TRANSFER -> {
                        if (isReversal) {
                            transfersIn = transfersIn.subtract(amt);
                            transfersOut = transfersOut.subtract(amt);
                        } else {
                            transfersIn = transfersIn.add(amt);
                            transfersOut = transfersOut.add(amt);
                        }
                    }
                    default -> {
                        if (tx.getMovementDirection() == MovementDirection.INCREASE) {
                            unclassifiedInflows = unclassifiedInflows.add(amt);
                        } else if (tx.getMovementDirection() == MovementDirection.DECREASE) {
                            unclassifiedOutflows = unclassifiedOutflows.add(amt);
                        }
                    }
                }
            }

            evidence.add(new FinancialEvidenceReference(
                    "FINANCIAL_TRANSACTION",
                    tx.getId() != null ? tx.getId().toString() : "UNKNOWN",
                    (tx.getEventType() != null ? tx.getEventType().name() : "UNCLASSIFIED") + " (" + formatAmount(amt, 2) + " " + tx.getCurrency() + ") on " + tx.getEffectiveDate(),
                    tx.getCreatedAt()
            ));
        }

        if (unclassifiedInflows.compareTo(BigDecimal.ZERO) > 0 || unclassifiedOutflows.compareTo(BigDecimal.ZERO) > 0) {
            isPartial = true;
            warnings.add("Found unclassified posted cash transactions: Inflow=" + formatAmount(unclassifiedInflows, 2) +
                    ", Outflow=" + formatAmount(unclassifiedOutflows, 2) + ". Included in cash totals without categorization.");
        }

        BigDecimal netDirectSales = grossDirectSales.subtract(revDirectSales);
        BigDecimal netRecCollections = grossRecCollections.subtract(revRecCollections);
        BigDecimal totalCashInflow = netDirectSales.add(netRecCollections).add(unclassifiedInflows);

        BigDecimal netOpexPayments = grossOpexPayments.subtract(revOpexPayments);
        BigDecimal netPaySettlements = grossPaySettlements.subtract(revPaySettlements);
        BigDecimal totalCashOutflow = netOpexPayments.add(netPaySettlements).add(unclassifiedOutflows);

        BigDecimal netOperatingCashFlow = totalCashInflow.subtract(totalCashOutflow);
        BigDecimal netTransfersAtBusinessLevel = transfersIn.subtract(transfersOut);
        BigDecimal totalNetCashMovement = netOperatingCashFlow.add(netTransfersAtBusinessLevel);

        MonthlyOperatingCashFlow cashFlow = new MonthlyOperatingCashFlow(
                "PKR",
                formatAmount(grossDirectSales, 2),
                formatAmount(revDirectSales, 2),
                formatAmount(netDirectSales, 2),
                formatAmount(grossRecCollections, 2),
                formatAmount(revRecCollections, 2),
                formatAmount(netRecCollections, 2),
                formatAmount(unclassifiedInflows, 2),
                formatAmount(totalCashInflow, 2),
                formatAmount(grossOpexPayments, 2),
                formatAmount(revOpexPayments, 2),
                formatAmount(netOpexPayments, 2),
                formatAmount(grossPaySettlements, 2),
                formatAmount(revPaySettlements, 2),
                formatAmount(netPaySettlements, 2),
                formatAmount(unclassifiedOutflows, 2),
                formatAmount(totalCashOutflow, 2),
                formatAmount(netOperatingCashFlow, 2),
                formatAmount(transfersIn, 2),
                formatAmount(transfersOut, 2),
                formatAmount(netTransfersAtBusinessLevel, 2),
                formatAmount(totalNetCashMovement, 2)
        );

        // 3. Recognized Figures (Accrual vs Cash & Earned Revenue Provenance)
        BigDecimal verifiedDirectSaleRevenue = netDirectSales;
        BigDecimal verifiedCashOperatingExpenses = netOpexPayments;

        List<FinancialObligation> allObligations = obligationRepository.findByBusinessId(context.businessId());
        BigDecimal invoiceIssuedEarnedRevenue = BigDecimal.ZERO;
        BigDecimal provisionalReceivableCandidates = BigDecimal.ZERO;
        BigDecimal billIncurredOperatingExpenses = BigDecimal.ZERO;
        BigDecimal provisionalPayableCandidates = BigDecimal.ZERO;

        for (FinancialObligation ob : allObligations) {
            if (ob.getStatus() == ObligationStatus.VOIDED) continue;
            LocalDate issueDate = ob.getIssueDate();
            if (issueDate != null && !issueDate.isBefore(startDate) && !issueDate.isAfter(endDate)) {
                BigDecimal obAmt = ob.getOriginalAmount() != null ? ob.getOriginalAmount() : BigDecimal.ZERO;
                if (ob.getDirection() == ObligationDirection.RECEIVABLE) {
                    if (ob.getSourceType() == ObligationSourceType.CUSTOMER_INVOICE) {
                        invoiceIssuedEarnedRevenue = invoiceIssuedEarnedRevenue.add(obAmt);
                    } else if (ob.getSourceType() == ObligationSourceType.MANUAL_DECLARATION) {
                        provisionalReceivableCandidates = provisionalReceivableCandidates.add(obAmt);
                    }
                    // OPENING_BALANCE and APPROVED_ADJUSTMENT are balance sheet items; excluded from earned revenue
                } else if (ob.getDirection() == ObligationDirection.PAYABLE) {
                    if (ob.getSourceType() == ObligationSourceType.SUPPLIER_BILL) {
                        billIncurredOperatingExpenses = billIncurredOperatingExpenses.add(obAmt);
                    } else if (ob.getSourceType() == ObligationSourceType.MANUAL_DECLARATION) {
                        provisionalPayableCandidates = provisionalPayableCandidates.add(obAmt);
                    }
                    // OPENING_BALANCE and APPROVED_ADJUSTMENT are balance sheet items; excluded from incurred opex
                }
            }
        }

        BigDecimal authoritativeAccrualRevenue = verifiedDirectSaleRevenue.add(invoiceIssuedEarnedRevenue);
        BigDecimal provisionalTotalRevenue = authoritativeAccrualRevenue.add(provisionalReceivableCandidates);

        BigDecimal authoritativeAccrualOperatingExpenses = verifiedCashOperatingExpenses.add(billIncurredOperatingExpenses);
        BigDecimal provisionalTotalOperatingExpenses = authoritativeAccrualOperatingExpenses.add(provisionalPayableCandidates);

        CapabilityStatus accrualRevenueStatus = (authoritativeAccrualRevenue.compareTo(BigDecimal.ZERO) > 0 || provisionalTotalRevenue.compareTo(BigDecimal.ZERO) == 0)
                ? CapabilityStatus.AVAILABLE
                : CapabilityStatus.UNAVAILABLE;

        DataCompleteness accrualRevenueCompleteness;
        if (provisionalReceivableCandidates.compareTo(BigDecimal.ZERO) == 0) {
            accrualRevenueCompleteness = authoritativeAccrualRevenue.compareTo(BigDecimal.ZERO) > 0 ? DataCompleteness.COMPLETE : DataCompleteness.EMPTY;
        } else {
            accrualRevenueCompleteness = DataCompleteness.PARTIAL;
            warnings.add("Provisional unprovenanced receivable obligations of " + formatAmount(provisionalReceivableCandidates, 2) +
                    " PKR exist in " + monthStr + " and are excluded from authoritative earned revenue.");
        }

        BigDecimal revLessKnownOpex = authoritativeAccrualRevenue.subtract(authoritativeAccrualOperatingExpenses);
        BigDecimal cashRevLessKnownOpex = verifiedDirectSaleRevenue.subtract(verifiedCashOperatingExpenses);

        MonthlyRecognizedFigures recognizedFigures = new MonthlyRecognizedFigures(
                "PKR",
                formatAmount(verifiedDirectSaleRevenue, 2),
                formatAmount(invoiceIssuedEarnedRevenue, 2),
                formatAmount(provisionalReceivableCandidates, 2),
                accrualRevenueStatus,
                accrualRevenueCompleteness,
                formatAmount(authoritativeAccrualRevenue, 2),
                formatAmount(provisionalTotalRevenue, 2),
                formatAmount(verifiedCashOperatingExpenses, 2),
                formatAmount(billIncurredOperatingExpenses, 2),
                formatAmount(provisionalPayableCandidates, 2),
                formatAmount(authoritativeAccrualOperatingExpenses, 2),
                formatAmount(provisionalTotalOperatingExpenses, 2),
                CapabilityStatus.UNAVAILABLE,
                null,
                "Inventory valuation and COGS (F08) are owned by Developer B and have not yet been integrated into the ledger. Zero substitution is rejected.",
                CapabilityStatus.UNAVAILABLE,
                null,
                "Gross profit requires COGS (F08), which is currently unavailable. Zero substitution is rejected.",
                CapabilityStatus.UNAVAILABLE,
                null,
                "Authoritative operating profit (EBIT/EBITDA) requires inventory costing and complete accrual recognition.",
                CapabilityStatus.UNAVAILABLE,
                null,
                "Net profit requires COGS, financing expense, and tax calculation.",
                formatAmount(revLessKnownOpex, 2),
                formatAmount(cashRevLessKnownOpex, 2),
                "Accrual earned revenue includes direct sale cash receipts plus verified customer invoices issued in " + monthStr +
                        ". Manual / unprovenanced receivables are tracked as provisional candidates. COGS and profit metrics are UNAVAILABLE pending F08; 'revenueLessKnownOperatingExpensesBeforeCogs' is a diagnostic metric, not operating profit."
        );

        // 4. Outstanding Obligation Positions as of End of Month
        LocalDate obligationAsOf = asOfDate.isBefore(endDate) ? asOfDate : endDate;
        ObligationAgingResult aging = obligationService.getAging(context, obligationAsOf.toString());

        BigDecimal totalRecOutstanding = aging.receivableTotalOutstanding();
        int recCount = aging.receivables() != null ? aging.receivables().totalActiveCount() : 0;
        BigDecimal totalPayOutstanding = aging.payableTotalOutstanding();
        int payCount = aging.payables() != null ? aging.payables().totalActiveCount() : 0;

        // Unapplied Advances as of obligationAsOf
        List<CounterpartyAdvance> advances = advanceRepository.findByBusinessId(context.businessId());
        BigDecimal customerAdvUnapplied = BigDecimal.ZERO;
        BigDecimal supplierAdvUnapplied = BigDecimal.ZERO;

        for (CounterpartyAdvance adv : advances) {
            if (adv.getStatus() != AdvanceStatus.ACTIVE) continue;
            if (adv.getEffectiveDate().isAfter(obligationAsOf)) continue;

            BigDecimal applied = allocationRepository.sumEffectiveAllocationsByAdvanceId(context.businessId(), adv.getId());
            BigDecimal unapplied = adv.getOriginalAmount().subtract(applied != null ? applied : BigDecimal.ZERO);
            if (unapplied.compareTo(BigDecimal.ZERO) < 0) unapplied = BigDecimal.ZERO;

            if (adv.getDirection() == ObligationDirection.RECEIVABLE) {
                customerAdvUnapplied = customerAdvUnapplied.add(unapplied);
            } else {
                supplierAdvUnapplied = supplierAdvUnapplied.add(unapplied);
            }
        }

        BigDecimal netWorkingCapital = totalRecOutstanding.subtract(totalPayOutstanding);

        MonthlyObligationPositions obligationPositions = new MonthlyObligationPositions(
                obligationAsOf,
                "PKR",
                formatAmount(totalRecOutstanding, 2),
                recCount,
                formatAmount(totalPayOutstanding, 2),
                payCount,
                formatAmount(customerAdvUnapplied, 2),
                formatAmount(supplierAdvUnapplied, 2),
                formatAmount(netWorkingCapital, 2)
        );

        // 5. Excluded Drafts & Recurring Projections
        int draftCount = 0;
        BigDecimal draftAmount = BigDecimal.ZERO;
        if (recordDraftRepository != null) {
            List<RecordDraft> drafts = recordDraftRepository.findByBusinessIdAndStatus(context.businessId(), DraftStatus.DRAFT);
            draftCount = drafts.size();
            for (RecordDraft d : drafts) {
                if (d.getAmountInput() != null) {
                    try {
                        draftAmount = draftAmount.add(new BigDecimal(d.getAmountInput().trim()));
                    } catch (Exception ignored) {}
                }
            }
        }

        int recurringCount = 0;
        BigDecimal recurringAmount = BigDecimal.ZERO;
        if (recurringOccurrenceRepository != null) {
            recurringCount = (int) recurringOccurrenceRepository.countByBusinessIdAndStatus(context.businessId(), RecurringOccurrenceStatus.SCHEDULED)
                    + (int) recurringOccurrenceRepository.countByBusinessIdAndStatus(context.businessId(), RecurringOccurrenceStatus.DRAFT_READY);
        }

        MonthlyDraftSummary draftSummary = new MonthlyDraftSummary(
                draftCount,
                formatAmount(draftAmount, 2),
                recurringCount,
                formatAmount(recurringAmount, 2),
                true
        );

        DataCompleteness completeness = accounts.isEmpty() && transactions.isEmpty() ? DataCompleteness.EMPTY :
                (isPartial ? DataCompleteness.PARTIAL : DataCompleteness.COMPLETE);

        return new MonthlyLedgerProjectionResponse(
                CONTRACT_VERSION,
                context.businessId(),
                monthStr,
                startDate,
                endDate,
                asOfDate,
                completeness,
                warnings,
                evidence,
                currencySummaries,
                accountPositions,
                cashFlow,
                recognizedFigures,
                obligationPositions,
                draftSummary
        );
    }

    /**
     * Executes shadow comparison between historical MonthlyRecord values and ledger-derived figures.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public MonthlyShadowComparisonResponse compareWithMonthlyRecord(
            BusinessAccessContext context,
            String monthStr,
            String asOfDateStr
    ) {
        requireReadPermission(context);

        MonthlyLedgerProjectionResponse projection = projectMonthlyLedger(context, monthStr, asOfDateStr);
        Optional<MonthlyRecord> recordOpt = monthlyRecordRepository.findByUserIdAndMonth(context.businessId(), monthStr);

        if (recordOpt.isEmpty()) {
            return new MonthlyShadowComparisonResponse(
                    CONTRACT_VERSION,
                    context.businessId(),
                    monthStr,
                    projection.asOfDate(),
                    false,
                    null,
                    Collections.emptyList(),
                    0,
                    0,
                    0,
                    "No historical MonthlyRecord found for month " + monthStr + ". Transaction ledger is the sole financial source.",
                    List.of("No manual MonthlyRecord declarations exist for this period.")
            );
        }

        MonthlyRecord rec = recordOpt.get();
        List<MonthlyMetricComparison> comparisons = new ArrayList<>();
        int matchingCount = 0;
        int diffCount = 0;
        int unsupportedCount = 0;

        // Extract primary PKR closing balance from ledger projection
        String ledgerPkrClosing = projection.currencySummaries().stream()
                .filter(s -> "PKR".equalsIgnoreCase(s.currency()))
                .map(CurrencyMonthlyCashSummary::totalClosingBalance)
                .findFirst()
                .orElse("0.00");

        // 1. Cash Inflow
        MetricEval evalInflow = evaluateMetricMatch(
                rec.getCashInflow(),
                new BigDecimal(projection.cashFlow().totalCashInflow()),
                2
        );
        comparisons.add(new MonthlyMetricComparison(
                "cashInflow",
                "Cash Inflow",
                formatAmount(rec.getCashInflow(), 2),
                projection.cashFlow().totalCashInflow(),
                evalInflow.diffStr(),
                evalInflow.status(),
                RecognitionBasis.CASH_FLOW,
                "MonthlyRecord cash inflow vs. ledger net non-transfer cash receipts (direct sale receipts + collections)"
        ));
        if (evalInflow.status() == ComparisonMetricStatus.MATCH) matchingCount++; else diffCount++;

        // 2. Cash Outflow
        MetricEval evalOutflow = evaluateMetricMatch(
                rec.getCashOutflow(),
                new BigDecimal(projection.cashFlow().totalCashOutflow()),
                2
        );
        comparisons.add(new MonthlyMetricComparison(
                "cashOutflow",
                "Cash Outflow",
                formatAmount(rec.getCashOutflow(), 2),
                projection.cashFlow().totalCashOutflow(),
                evalOutflow.diffStr(),
                evalOutflow.status(),
                RecognitionBasis.CASH_FLOW,
                "MonthlyRecord cash outflow vs. ledger net non-transfer cash disbursements (operating expenses + settlements)"
        ));
        if (evalOutflow.status() == ComparisonMetricStatus.MATCH) matchingCount++; else diffCount++;

        // 3. Net Cash Flow
        BigDecimal recNetCash = (rec.getCashInflow() != null ? rec.getCashInflow() : BigDecimal.ZERO)
                .subtract(rec.getCashOutflow() != null ? rec.getCashOutflow() : BigDecimal.ZERO);
        MetricEval evalNet = evaluateMetricMatch(
                recNetCash,
                new BigDecimal(projection.cashFlow().netOperatingCashFlow()),
                2
        );
        comparisons.add(new MonthlyMetricComparison(
                "netCashFlow",
                "Net Cash Flow",
                formatAmount(recNetCash, 2),
                projection.cashFlow().netOperatingCashFlow(),
                evalNet.diffStr(),
                evalNet.status(),
                RecognitionBasis.CASH_FLOW,
                "Net monthly operating cash movement (Cash Inflow minus Cash Outflow)"
        ));
        if (evalNet.status() == ComparisonMetricStatus.MATCH) matchingCount++; else diffCount++;

        // 4. Cash Balance EOM
        MetricEval evalBal = evaluateMetricMatch(
                rec.getCashBalanceEom(),
                new BigDecimal(ledgerPkrClosing),
                2
        );
        comparisons.add(new MonthlyMetricComparison(
                "cashBalanceEom",
                "Closing Cash Balance (EOM)",
                formatAmount(rec.getCashBalanceEom(), 2),
                ledgerPkrClosing,
                evalBal.diffStr(),
                evalBal.status(),
                RecognitionBasis.POINT_IN_TIME_BALANCE,
                "MonthlyRecord reported end-of-month cash balance vs. ledger sum of active account closing balances"
        ));
        if (evalBal.status() == ComparisonMetricStatus.MATCH) matchingCount++; else diffCount++;

        // 5. Revenue
        MetricEval evalRev = evaluateMetricMatch(
                rec.getRevenue(),
                new BigDecimal(projection.recognizedFigures().authoritativeAccrualRevenue()),
                2
        );
        String revNote = "MonthlyRecord revenue vs. ledger authoritative earned revenue (direct sales: " +
                projection.recognizedFigures().verifiedDirectSaleRevenue() + ", customer invoices: " +
                projection.recognizedFigures().invoiceIssuedEarnedRevenue() + ").";
        if (new BigDecimal(projection.recognizedFigures().provisionalReceivableCandidates()).compareTo(BigDecimal.ZERO) > 0) {
            revNote += " Provisional unprovenanced receivable candidates of PKR " +
                    projection.recognizedFigures().provisionalReceivableCandidates() + " are excluded from authoritative earned revenue.";
        }
        comparisons.add(new MonthlyMetricComparison(
                "revenue",
                "Revenue",
                formatAmount(rec.getRevenue(), 2),
                projection.recognizedFigures().authoritativeAccrualRevenue(),
                evalRev.diffStr(),
                evalRev.status(),
                RecognitionBasis.ACCRUAL_ACCOUNTING,
                revNote
        ));
        if (evalRev.status() == ComparisonMetricStatus.MATCH) matchingCount++; else diffCount++;

        // 6. Operating Expenses
        MetricEval evalOpex = evaluateMetricMatch(
                rec.getOperatingExpenses(),
                new BigDecimal(projection.recognizedFigures().authoritativeAccrualOperatingExpenses()),
                2
        );
        String opexNote = "MonthlyRecord operating expenses vs. ledger authoritative accrual opex (cash opex: " +
                projection.recognizedFigures().verifiedCashOperatingExpenses() + ", supplier bills: " +
                projection.recognizedFigures().billIncurredOperatingExpenses() + ").";
        if (new BigDecimal(projection.recognizedFigures().provisionalPayableCandidates()).compareTo(BigDecimal.ZERO) > 0) {
            opexNote += " Provisional unprovenanced payable candidates of PKR " +
                    projection.recognizedFigures().provisionalPayableCandidates() + " are excluded from authoritative opex.";
        }
        comparisons.add(new MonthlyMetricComparison(
                "operatingExpenses",
                "Operating Expenses",
                formatAmount(rec.getOperatingExpenses(), 2),
                projection.recognizedFigures().authoritativeAccrualOperatingExpenses(),
                evalOpex.diffStr(),
                evalOpex.status(),
                RecognitionBasis.ACCRUAL_ACCOUNTING,
                opexNote
        ));
        if (evalOpex.status() == ComparisonMetricStatus.MATCH) matchingCount++; else diffCount++;

        // 7. Receivables Outstanding
        MetricEval evalRec = evaluateMetricMatch(
                rec.getReceivablesOutstanding(),
                new BigDecimal(projection.obligationPositions().totalReceivablesOutstanding()),
                2
        );
        comparisons.add(new MonthlyMetricComparison(
                "receivablesOutstanding",
                "Receivables Outstanding",
                formatAmount(rec.getReceivablesOutstanding(), 2),
                projection.obligationPositions().totalReceivablesOutstanding(),
                evalRec.diffStr(),
                evalRec.status(),
                RecognitionBasis.POINT_IN_TIME_BALANCE,
                "MonthlyRecord unsettled receivables vs. canonical obligation aging outstanding balance as of month end"
        ));
        if (evalRec.status() == ComparisonMetricStatus.MATCH) matchingCount++; else diffCount++;

        // 8. Payables Outstanding
        MetricEval evalPay = evaluateMetricMatch(
                rec.getPayablesOutstanding(),
                new BigDecimal(projection.obligationPositions().totalPayablesOutstanding()),
                2
        );
        comparisons.add(new MonthlyMetricComparison(
                "payablesOutstanding",
                "Payables Outstanding",
                formatAmount(rec.getPayablesOutstanding(), 2),
                projection.obligationPositions().totalPayablesOutstanding(),
                evalPay.diffStr(),
                evalPay.status(),
                RecognitionBasis.POINT_IN_TIME_BALANCE,
                "MonthlyRecord unsettled payables vs. canonical obligation aging outstanding balance as of month end"
        ));
        if (evalPay.status() == ComparisonMetricStatus.MATCH) matchingCount++; else diffCount++;

        // 9. COGS (Unsupported in Ledger - Zero Substitution Strictly Rejected)
        comparisons.add(new MonthlyMetricComparison(
                "cogs",
                "Cost of Goods Sold (COGS)",
                formatAmount(rec.getCogs(), 2),
                null,
                null,
                ComparisonMetricStatus.NOT_SUPPORTED_IN_LEDGER,
                RecognitionBasis.UNINTEGRATED_MODULE,
                "COGS is owned by Developer B (F08: Inventory Valuation & COGS) and is not yet available in the transaction ledger. Zero substitution is strictly rejected."
        ));
        unsupportedCount++;

        // 10. Supplemental Declarations
        comparisons.add(new MonthlyMetricComparison(
                "inventoryValue",
                "Inventory Value",
                formatAmount(rec.getInventoryValue(), 2),
                null,
                null,
                ComparisonMetricStatus.NOT_SUPPORTED_IN_LEDGER,
                RecognitionBasis.SUPPLEMENTAL_DECLARATION,
                "Supplemental user declaration; ledger tracking depends on future F08 inventory module"
        ));
        unsupportedCount++;

        comparisons.add(new MonthlyMetricComparison(
                "loanOutstanding",
                "Loan Outstanding",
                formatAmount(rec.getLoanOutstanding(), 2),
                null,
                null,
                ComparisonMetricStatus.NOT_SUPPORTED_IN_LEDGER,
                RecognitionBasis.SUPPLEMENTAL_DECLARATION,
                "Supplemental user declaration; financing liabilities are not modeled as standalone bank accounts"
        ));
        unsupportedCount++;

        comparisons.add(new MonthlyMetricComparison(
                "interestExpense",
                "Interest Expense",
                formatAmount(rec.getInterestExpense(), 2),
                null,
                null,
                ComparisonMetricStatus.NOT_SUPPORTED_IN_LEDGER,
                RecognitionBasis.SUPPLEMENTAL_DECLARATION,
                "Supplemental user declaration; interest payments are currently recorded within operating expenses"
        ));
        unsupportedCount++;

        String summary = String.format(
                "Shadow comparison completed for month %s: %d metrics match, %d differ, %d unintegrated/declaration-only.",
                monthStr, matchingCount, diffCount, unsupportedCount
        );

        List<String> basisNotes = List.of(
                "Cash Flow metrics reflect net non-transfer movements in Asia/Karachi timezone.",
                "Accrual metrics separate cash collections/settlements from legally recognized obligation issuance.",
                "Discrepancies do not necessarily indicate data errors; they reflect differences between cash-accounting records and double-entry ledger postings.",
                "COGS and Inventory Valuation require Developer B (Fatima F08) integration before automated ledger cutover."
        );

        return new MonthlyShadowComparisonResponse(
                CONTRACT_VERSION,
                context.businessId(),
                monthStr,
                projection.asOfDate(),
                true,
                rec.getId(),
                comparisons,
                matchingCount,
                diffCount,
                unsupportedCount,
                summary,
                basisNotes
        );
    }

    private void requireReadPermission(BusinessAccessContext context) {
        if (context == null || context.businessId() == null) {
            throw new IllegalArgumentException("Business context is required");
        }
        if (!authorizationService.hasRolePermission(context.role(), BusinessPermission.FINANCIAL_DATA_READ)) {
            throw new AccessDeniedException("Role " + context.role() + " lacks FINANCIAL_DATA_READ permission");
        }
    }

    private YearMonth parseYearMonth(String monthStr) {
        if (monthStr == null || monthStr.isBlank()) {
            throw new FinancialQueryValidationException("month", "Month is required (YYYY-MM)");
        }
        try {
            return YearMonth.parse(monthStr.trim());
        } catch (DateTimeParseException e) {
            throw new FinancialQueryValidationException("month", "Invalid month format. Expected YYYY-MM");
        }
    }

    private LocalDate parseOptionalAsOfDate(String asOfDateStr, LocalDate defaultDate) {
        if (asOfDateStr == null || asOfDateStr.isBlank()) {
            return defaultDate;
        }
        try {
            return LocalDate.parse(asOfDateStr.trim());
        } catch (DateTimeParseException e) {
            throw new FinancialQueryValidationException("asOfDate", "Invalid date format. Expected YYYY-MM-DD");
        }
    }

    private Map<UUID, BigDecimal> fetchNetMovementsUpToDate(UUID businessId, LocalDate cutoffDate) {
        Map<UUID, BigDecimal> map = new HashMap<>();
        List<AccountMovementSum> sums = movementRepository.findMovementSumsByBusinessIdAndCutoffDate(businessId, cutoffDate);
        if (sums != null) {
            for (AccountMovementSum s : sums) {
                if (s == null || s.getAccountId() == null || s.getTotalAmount() == null) continue;
                BigDecimal cur = map.getOrDefault(s.getAccountId(), BigDecimal.ZERO);
                if (s.getMovementDirection() == MovementDirection.INCREASE) {
                    map.put(s.getAccountId(), cur.add(s.getTotalAmount()));
                } else if (s.getMovementDirection() == MovementDirection.DECREASE) {
                    map.put(s.getAccountId(), cur.subtract(s.getTotalAmount()));
                }
            }
        }
        return map;
    }

    private Map<UUID, BigDecimal> fetchNetMovementsBetweenDates(UUID businessId, LocalDate startDate, LocalDate endDate) {
        Map<UUID, BigDecimal> map = new HashMap<>();
        List<AccountMovementSum> sums = movementRepository.findMovementSumsByBusinessIdAndDateRange(businessId, startDate, endDate);
        if (sums != null) {
            for (AccountMovementSum s : sums) {
                if (s == null || s.getAccountId() == null || s.getTotalAmount() == null) continue;
                BigDecimal cur = map.getOrDefault(s.getAccountId(), BigDecimal.ZERO);
                if (s.getMovementDirection() == MovementDirection.INCREASE) {
                    map.put(s.getAccountId(), cur.add(s.getTotalAmount()));
                } else if (s.getMovementDirection() == MovementDirection.DECREASE) {
                    map.put(s.getAccountId(), cur.subtract(s.getTotalAmount()));
                }
            }
        }
        return map;
    }

    private BigDecimal fetchAccountDirectionalTotal(
            UUID businessId,
            UUID accountId,
            MovementDirection direction,
            LocalDate startDate,
            LocalDate endDate
    ) {
        List<AccountMovementSum> sums = movementRepository.findMovementSumsByBusinessIdAndDateRange(businessId, startDate, endDate);
        if (sums == null) return BigDecimal.ZERO;
        for (AccountMovementSum s : sums) {
            if (s != null && accountId.equals(s.getAccountId()) && direction == s.getMovementDirection() && s.getTotalAmount() != null) {
                return s.getTotalAmount();
            }
        }
        return BigDecimal.ZERO;
    }

    private String formatAmount(BigDecimal amount, int decimals) {
        if (amount == null) return null;
        BigDecimal scaled = amount.setScale(decimals, RoundingMode.UNNECESSARY);
        if (scaled.signum() == 0) {
            return BigDecimal.ZERO.setScale(decimals, RoundingMode.UNNECESSARY).toPlainString();
        }
        return scaled.toPlainString();
    }

    private MetricEval evaluateMetricMatch(BigDecimal recordVal, BigDecimal ledgerVal, int scale) {
        if (recordVal == null && ledgerVal == null) {
            return new MetricEval(ComparisonMetricStatus.MATCH, "0.00");
        }
        if (recordVal == null) {
            return new MetricEval(ComparisonMetricStatus.NOT_PRESENT_IN_RECORD, formatAmount(ledgerVal, scale));
        }
        if (ledgerVal == null) {
            return new MetricEval(ComparisonMetricStatus.NOT_SUPPORTED_IN_LEDGER, null);
        }

        BigDecimal recScaled = recordVal.setScale(scale, RoundingMode.UNNECESSARY);
        BigDecimal ledScaled = ledgerVal.setScale(scale, RoundingMode.UNNECESSARY);
        BigDecimal diff = ledScaled.subtract(recScaled);

        ComparisonMetricStatus status = diff.signum() == 0
                ? ComparisonMetricStatus.MATCH
                : ComparisonMetricStatus.DIFFERENCE;

        return new MetricEval(status, formatAmount(diff, scale));
    }

    private record MetricEval(ComparisonMetricStatus status, String diffStr) {}
}
