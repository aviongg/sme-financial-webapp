package com.app.sme_health_backend.obligation.aging;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Pure, deterministic calculation engine for receivables and payables aging.
 * <p>
 * Evaluates obligations as of an explicit historical date, computing outstanding balances
 * from dated settlement adjustments, enforcing chronological balance validity, and
 * categorizing active obligations into standard aging buckets.
 */
public final class ObligationAgingCalculator {

    private static final String SUPPORTED_CURRENCY = "PKR";
    private static final BigDecimal ZERO_PKR = BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);

    private ObligationAgingCalculator() {
        // Utility class
    }

    /**
     * Calculates receivables and payables aging as of the specified historical date.
     *
     * @param asOfDate    effective historical calculation date
     * @param currency    iso currency code (strictly PKR in this version)
     * @param obligations collection of obligation histories to evaluate
     * @return immutable calculation result with summaries and deterministic details
     * @throws ObligationAgingException on any malformed input, invalid date, or balance violation
     */
    public static ObligationAgingResult calculate(
            LocalDate asOfDate,
            String currency,
            Collection<ObligationHistory> obligations
    ) {
        validateAsOfDate(asOfDate);
        validateCurrency(currency);
        if (obligations == null) {
            throw new ObligationAgingException("obligations", "Obligations collection cannot be null");
        }

        Set<String> seenObligationIds = new HashSet<>();
        List<ObligationAgingDetail> details = new ArrayList<>(obligations.size());

        for (ObligationHistory obligation : obligations) {
            if (obligation == null) {
                throw new ObligationAgingException("obligation", "Obligation entry cannot be null");
            }
            validateObligation(obligation, seenObligationIds);

            BigDecimal normalizedOriginalAmount = validateAndScaleAmount(
                    "originalAmount", obligation.originalAmount(), obligation.obligationId());

            // Validate settlement adjustments and compute running balance across full history
            Map<LocalDate, BigDecimal> netByDate = validateAdjustmentsAndNetByDate(obligation, normalizedOriginalAmount);

            // Compute outstanding as of the requested calculation date
            BigDecimal asOfOutstanding = normalizedOriginalAmount;
            for (Map.Entry<LocalDate, BigDecimal> entry : netByDate.entrySet()) {
                if (!entry.getKey().isAfter(asOfDate)) {
                    asOfOutstanding = asOfOutstanding.add(entry.getValue());
                }
            }

            // Determine visibility, voiding and aging bucket
            ObligationExclusionStatus exclusionStatus = null;
            AgingBucket assignedBucket = null;
            Integer daysOverdue = null;

            if (obligation.issueDate().isAfter(asOfDate)) {
                exclusionStatus = ObligationExclusionStatus.NOT_YET_ISSUED;
            } else if (obligation.voidDate() != null && !obligation.voidDate().isAfter(asOfDate)) {
                exclusionStatus = ObligationExclusionStatus.VOIDED;
            } else if (asOfOutstanding.compareTo(BigDecimal.ZERO) == 0) {
                exclusionStatus = ObligationExclusionStatus.SETTLED;
            } else {
                if (obligation.dueDate() == null) {
                    assignedBucket = AgingBucket.UNKNOWN_DUE_DATE;
                } else {
                    long days = ChronoUnit.DAYS.between(obligation.dueDate(), asOfDate);
                    if (days < 0) {
                        assignedBucket = AgingBucket.NOT_YET_DUE;
                    } else if (days == 0) {
                        assignedBucket = AgingBucket.DUE_TODAY;
                    } else {
                        daysOverdue = (int) days;
                        if (days <= 30) {
                            assignedBucket = AgingBucket.OVERDUE_1_30;
                        } else if (days <= 60) {
                            assignedBucket = AgingBucket.OVERDUE_31_60;
                        } else if (days <= 90) {
                            assignedBucket = AgingBucket.OVERDUE_61_90;
                        } else {
                            assignedBucket = AgingBucket.OVERDUE_91_PLUS;
                        }
                    }
                }
            }

            details.add(new ObligationAgingDetail(
                    obligation.obligationId(),
                    obligation.direction(),
                    obligation.issueDate(),
                    obligation.dueDate(),
                    normalizedOriginalAmount,
                    asOfOutstanding,
                    daysOverdue,
                    assignedBucket,
                    exclusionStatus
            ));
        }

        // Deterministic detail ordering:
        // 1. Direction: RECEIVABLE then PAYABLE
        // 2. IssueDate: ascending
        // 3. ObligationId: ascending (natural string order)
        details.sort(Comparator
                .comparing(ObligationAgingDetail::direction)
                .thenComparing(ObligationAgingDetail::issueDate)
                .thenComparing(ObligationAgingDetail::obligationId)
        );

        DirectionAgingSummary receivables = buildSummary(ObligationDirection.RECEIVABLE, details);
        DirectionAgingSummary payables = buildSummary(ObligationDirection.PAYABLE, details);

        return new ObligationAgingResult(asOfDate, SUPPORTED_CURRENCY, receivables, payables, details);
    }

    private static void validateAsOfDate(LocalDate asOfDate) {
        if (asOfDate == null) {
            throw new ObligationAgingException("asOfDate", "As-of date cannot be null");
        }
    }

    private static void validateCurrency(String currency) {
        if (currency == null || !SUPPORTED_CURRENCY.equalsIgnoreCase(currency.trim())) {
            throw new ObligationAgingException("currency",
                    "Unsupported currency: '" + currency + "'. Only PKR is supported in this version.");
        }
    }

    private static void validateObligation(ObligationHistory obligation, Set<String> seenObligationIds) {
        if (obligation.obligationId() == null || obligation.obligationId().isBlank()) {
            throw new ObligationAgingException("obligationId", "Obligation ID is required and cannot be blank");
        }
        if (!seenObligationIds.add(obligation.obligationId())) {
            throw new ObligationAgingException("obligationId", "Duplicate obligation ID: " + obligation.obligationId());
        }
        if (obligation.direction() == null) {
            throw new ObligationAgingException("direction",
                    "Obligation direction is required for obligation: " + obligation.obligationId());
        }
        if (obligation.issueDate() == null) {
            throw new ObligationAgingException("issueDate",
                    "Issue date is required for obligation: " + obligation.obligationId());
        }
        if (obligation.dueDate() != null && obligation.dueDate().isBefore(obligation.issueDate())) {
            throw new ObligationAgingException("dueDate",
                    "Due date (" + obligation.dueDate() + ") cannot be before issue date (" +
                            obligation.issueDate() + ") for obligation: " + obligation.obligationId());
        }
        if (obligation.voidDate() != null && obligation.voidDate().isBefore(obligation.issueDate())) {
            throw new ObligationAgingException("voidDate",
                    "Void date (" + obligation.voidDate() + ") cannot be before issue date (" +
                            obligation.issueDate() + ") for obligation: " + obligation.obligationId());
        }
    }

    private static BigDecimal validateAndScaleAmount(String field, BigDecimal amount, String obligationId) {
        if (amount == null) {
            throw new ObligationAgingException(field, field + " is required for obligation: " + obligationId);
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ObligationAgingException(field, field + " must be strictly positive for obligation: " + obligationId);
        }
        if (amount.scale() > 2 && amount.stripTrailingZeros().scale() > 2) {
            throw new ObligationAgingException(field,
                    field + " cannot have more than 2 decimal places (" + amount + ") for obligation: " + obligationId);
        }
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new ObligationAgingException(field,
                    field + " cannot be represented exactly at 2 decimal places without rounding: " + amount);
        }
    }

    /**
     * Groups adjustments by effectiveDate, validates individual adjustment fields and uniqueness,
     * and performs chronological running balance validation across the full history.
     */
    private static Map<LocalDate, BigDecimal> validateAdjustmentsAndNetByDate(
            ObligationHistory obligation,
            BigDecimal normalizedOriginalAmount
    ) {
        Set<String> seenAdjustmentIds = new HashSet<>();
        Map<LocalDate, BigDecimal> netByDate = new TreeMap<>();

        for (SettlementAdjustment adj : obligation.adjustments()) {
            if (adj == null) {
                throw new ObligationAgingException("adjustments",
                        "Adjustment entry cannot be null for obligation: " + obligation.obligationId());
            }
            if (adj.adjustmentId() == null || adj.adjustmentId().isBlank()) {
                throw new ObligationAgingException("adjustmentId",
                        "Adjustment ID cannot be blank in obligation: " + obligation.obligationId());
            }
            if (!seenAdjustmentIds.add(adj.adjustmentId())) {
                throw new ObligationAgingException("adjustmentId",
                        "Duplicate adjustment ID '" + adj.adjustmentId() + "' in obligation: " + obligation.obligationId());
            }
            if (adj.type() == null) {
                throw new ObligationAgingException("type",
                        "Adjustment type is required for adjustment: " + adj.adjustmentId());
            }
            if (adj.effectiveDate() == null) {
                throw new ObligationAgingException("effectiveDate",
                        "Adjustment effective date is required for adjustment: " + adj.adjustmentId());
            }
            if (adj.effectiveDate().isBefore(obligation.issueDate())) {
                throw new ObligationAgingException("effectiveDate",
                        "Adjustment effective date (" + adj.effectiveDate() +
                                ") cannot be before issue date (" + obligation.issueDate() +
                                ") for obligation: " + obligation.obligationId());
            }

            BigDecimal normalizedAmount = validateAndScaleAmount(
                    "adjustmentAmount", adj.amount(), obligation.obligationId());

            BigDecimal delta = (adj.type() == SettlementAdjustmentType.RESTORE_OUTSTANDING)
                    ? normalizedAmount
                    : normalizedAmount.negate();

            netByDate.merge(adj.effectiveDate(), delta, BigDecimal::add);
        }

        // Validate running balance at end of each date across the full history
        BigDecimal running = normalizedOriginalAmount;
        for (Map.Entry<LocalDate, BigDecimal> entry : netByDate.entrySet()) {
            LocalDate date = entry.getKey();
            BigDecimal netChange = entry.getValue();
            running = running.add(netChange);

            if (running.compareTo(BigDecimal.ZERO) < 0) {
                throw new ObligationAgingException("adjustments",
                        "Running outstanding balance became negative (" + running + ") on date " + date +
                                " for obligation: " + obligation.obligationId());
            }
            if (running.compareTo(normalizedOriginalAmount) > 0) {
                throw new ObligationAgingException("adjustments",
                        "Running outstanding balance (" + running + ") exceeded original amount (" +
                                normalizedOriginalAmount + ") on date " + date +
                                " for obligation: " + obligation.obligationId());
            }
        }

        return netByDate;
    }

    private static DirectionAgingSummary buildSummary(
            ObligationDirection direction,
            List<ObligationAgingDetail> details
    ) {
        BigDecimal totalOutstanding = ZERO_PKR;
        BigDecimal overdueOutstanding = ZERO_PKR;
        int totalActiveCount = 0;
        int overdueCount = 0;
        int settledCount = 0;
        int voidedCount = 0;
        int notYetIssuedCount = 0;

        Map<AgingBucket, BigDecimal> bucketTotals = new EnumMap<>(AgingBucket.class);
        Map<AgingBucket, Integer> bucketCounts = new EnumMap<>(AgingBucket.class);
        for (AgingBucket b : AgingBucket.values()) {
            bucketTotals.put(b, ZERO_PKR);
            bucketCounts.put(b, 0);
        }

        for (ObligationAgingDetail d : details) {
            if (d.direction() != direction) {
                continue;
            }

            if (d.isActive()) {
                totalActiveCount++;
                totalOutstanding = totalOutstanding.add(d.asOfOutstanding());

                AgingBucket bucket = d.assignedBucket();
                bucketTotals.put(bucket, bucketTotals.get(bucket).add(d.asOfOutstanding()));
                bucketCounts.put(bucket, bucketCounts.get(bucket) + 1);

                if (d.isOverdue()) {
                    overdueCount++;
                    overdueOutstanding = overdueOutstanding.add(d.asOfOutstanding());
                }
            } else {
                if (d.exclusionStatus() == ObligationExclusionStatus.SETTLED) {
                    settledCount++;
                } else if (d.exclusionStatus() == ObligationExclusionStatus.VOIDED) {
                    voidedCount++;
                } else if (d.exclusionStatus() == ObligationExclusionStatus.NOT_YET_ISSUED) {
                    notYetIssuedCount++;
                }
            }
        }

        Map<AgingBucket, BucketSummary> buckets = new EnumMap<>(AgingBucket.class);
        for (AgingBucket b : AgingBucket.values()) {
            buckets.put(b, new BucketSummary(b, bucketTotals.get(b), bucketCounts.get(b)));
        }

        return new DirectionAgingSummary(
                direction,
                totalOutstanding,
                overdueOutstanding,
                totalActiveCount,
                overdueCount,
                buckets,
                settledCount,
                voidedCount,
                notYetIssuedCount
        );
    }
}
