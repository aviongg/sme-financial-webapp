package com.app.sme_health_backend.liquidity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure, deterministic calculation engine determining project funding gaps.
 * <p>
 * Answers: "How much additional funding does this project need, and when?"
 * Operates strictly with exact {@link BigDecimal} arithmetic in PKR.
 * <p>
 * Semantics & Invariants:
 * <ul>
 *   <li>PKR-only first version with exact scale-2 decimal arithmetic.</li>
 *   <li>Opening funds represent funds explicitly dedicated to this calculation. Missing opening funds fail validation; explicit zero is valid.</li>
 *   <li>Within-day cash flow events are netted into daily aggregates. This estimates end-of-day funding needs and does not guarantee intra-day liquidity at every instant.</li>
 *   <li>The returned daily curve is continuous across all calendar days in the horizon, including days without events.</li>
 *   <li>Event ordering in the input has zero effect on the calculation outcome.</li>
 *   <li>The daily curve reflects the project cash trajectory before any hypothetical funding injection.</li>
 * </ul>
 */
public class ProjectFundingGapCalculator {

    public static final String SUPPORTED_CURRENCY = "PKR";
    public static final BigDecimal ZERO_PKR = BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);

    /**
     * Static convenience delegate for calculating a project funding gap.
     *
     * @param input validated plan input
     * @return immutable calculation result
     * @throws ProjectFundingGapException if input validation fails
     */
    public static ProjectFundingGapResult calculateGap(ProjectFundingPlanInput input) {
        return new ProjectFundingGapCalculator().calculate(input);
    }

    /**
     * Calculates the funding gap, key shortfall/recovery milestones, and continuous daily curve.
     *
     * @param input project plan input parameters
     * @return immutable calculation result
     * @throws ProjectFundingGapException if input validation fails
     */
    public ProjectFundingGapResult calculate(ProjectFundingPlanInput input) {
        validateInput(input);

        BigDecimal openingFunds = normalizeAmount("openingFunds", input.openingFunds(), false);
        LocalDate startDate = input.horizonStart();
        LocalDate endDate = input.horizonEnd();

        // Aggregate event amounts by date and direction
        Map<LocalDate, BigDecimal> dailyInflows = new HashMap<>();
        Map<LocalDate, BigDecimal> dailyOutflows = new HashMap<>();
        BigDecimal totalInflow = ZERO_PKR;
        BigDecimal totalOutflow = ZERO_PKR;

        for (PlannedProjectCashEvent event : input.events()) {
            BigDecimal amt = normalizeAmount("events.amount", event.amount(), true);
            LocalDate date = event.date();
            if (event.direction() == ProjectCashFlowDirection.INFLOW) {
                dailyInflows.merge(date, amt, BigDecimal::add);
                totalInflow = totalInflow.add(amt);
            } else if (event.direction() == ProjectCashFlowDirection.OUTFLOW) {
                dailyOutflows.merge(date, amt, BigDecimal::add);
                totalOutflow = totalOutflow.add(amt);
            }
        }

        // Build continuous daily curve across entire calendar horizon
        List<DailyProjectFundingPosition> dailyCurve = new ArrayList<>();
        BigDecimal currentPosition = openingFunds;
        BigDecimal minPosition = null;
        LocalDate firstShortfallDate = null;
        LocalDate firstPeakShortfallDate = null;
        int shortfallDayCount = 0;

        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            BigDecimal dayInflow = dailyInflows.getOrDefault(d, ZERO_PKR);
            BigDecimal dayOutflow = dailyOutflows.getOrDefault(d, ZERO_PKR);
            BigDecimal dayNet = dayInflow.subtract(dayOutflow);
            currentPosition = currentPosition.add(dayNet);

            dailyCurve.add(new DailyProjectFundingPosition(d, dayInflow, dayOutflow, dayNet, currentPosition));

            if (minPosition == null || currentPosition.compareTo(minPosition) < 0) {
                minPosition = currentPosition;
            }

            if (currentPosition.compareTo(BigDecimal.ZERO) < 0) {
                shortfallDayCount++;
                if (firstShortfallDate == null) {
                    firstShortfallDate = d;
                }
            }
        }

        // Additional funding required = max(0, -minimum closing position)
        BigDecimal additionalFundingRequired;
        if (minPosition != null && minPosition.compareTo(BigDecimal.ZERO) < 0) {
            additionalFundingRequired = minPosition.negate();
            // Identify earliest date reaching the minimum negative position
            for (DailyProjectFundingPosition pos : dailyCurve) {
                if (pos.closingPosition().compareTo(minPosition) == 0) {
                    firstPeakShortfallDate = pos.date();
                    break;
                }
            }
        } else {
            additionalFundingRequired = ZERO_PKR;
            firstShortfallDate = null;
            firstPeakShortfallDate = null;
        }

        // Recovery date: earliest date after firstShortfallDate from which closing positions
        // remain non-negative through the supplied horizon.
        LocalDate recoveryDate = null;
        if (firstShortfallDate != null && !dailyCurve.isEmpty()) {
            DailyProjectFundingPosition lastDay = dailyCurve.get(dailyCurve.size() - 1);
            if (lastDay.closingPosition().compareTo(BigDecimal.ZERO) >= 0) {
                int lastNegativeIndex = -1;
                for (int i = dailyCurve.size() - 1; i >= 0; i--) {
                    if (dailyCurve.get(i).closingPosition().compareTo(BigDecimal.ZERO) < 0) {
                        lastNegativeIndex = i;
                        break;
                    }
                }
                if (lastNegativeIndex != -1 && lastNegativeIndex + 1 < dailyCurve.size()) {
                    recoveryDate = dailyCurve.get(lastNegativeIndex + 1).date();
                }
            }
        }

        BigDecimal finalClosingPosition = dailyCurve.isEmpty()
                ? openingFunds
                : dailyCurve.get(dailyCurve.size() - 1).closingPosition();

        return new ProjectFundingGapResult(
                input.currency(),
                openingFunds,
                startDate,
                endDate,
                totalInflow,
                totalOutflow,
                finalClosingPosition,
                additionalFundingRequired,
                firstShortfallDate,
                firstPeakShortfallDate,
                recoveryDate,
                shortfallDayCount,
                dailyCurve
        );
    }

    private void validateInput(ProjectFundingPlanInput input) {
        if (input == null) {
            throw new ProjectFundingGapException("input", "Plan input cannot be null");
        }

        // Currency validation (strictly PKR)
        if (input.currency() == null || input.currency().isBlank()) {
            throw new ProjectFundingGapException("currency", "Currency is required");
        }
        if (!SUPPORTED_CURRENCY.equalsIgnoreCase(input.currency().trim())) {
            throw new ProjectFundingGapException("currency",
                    "Unsupported currency '" + input.currency() + "'; calculator version is strictly PKR-only");
        }

        // Horizon validation
        LocalDate startDate = input.horizonStart();
        LocalDate endDate = input.horizonEnd();
        if (startDate == null) {
            throw new ProjectFundingGapException("horizonStart", "Horizon start date is required");
        }
        if (endDate == null) {
            throw new ProjectFundingGapException("horizonEnd", "Horizon end date is required");
        }
        if (startDate.isAfter(endDate)) {
            throw new ProjectFundingGapException("horizon",
                    "Horizon start date (" + startDate + ") cannot be after horizon end date (" + endDate + ")");
        }

        // Opening funds validation
        if (input.openingFunds() == null) {
            throw new ProjectFundingGapException("openingFunds",
                    "Opening funds are required (missing opening funds must fail validation; explicit zero is valid)");
        }
        normalizeAmount("openingFunds", input.openingFunds(), false);

        // Events validation
        if (input.events() == null) {
            throw new ProjectFundingGapException("events", "Events list is required");
        }

        Set<String> seenEventIds = new HashSet<>();
        for (PlannedProjectCashEvent event : input.events()) {
            if (event == null) {
                throw new ProjectFundingGapException("events", "Planned cash event cannot be null");
            }
            if (event.eventId() == null || event.eventId().isBlank()) {
                throw new ProjectFundingGapException("eventId", "Event ID is required and cannot be blank");
            }
            String trimmedId = event.eventId().trim();
            if (!seenEventIds.add(trimmedId)) {
                throw new ProjectFundingGapException("eventId", "Duplicate event ID: '" + event.eventId() + "'");
            }
            if (event.date() == null) {
                throw new ProjectFundingGapException("date", "Event date is required for event '" + event.eventId() + "'");
            }
            if (event.date().isBefore(startDate) || event.date().isAfter(endDate)) {
                throw new ProjectFundingGapException("date",
                        "Event date (" + event.date() + ") for event '" + event.eventId()
                                + "' is outside horizon [" + startDate + " to " + endDate + "]");
            }
            if (event.direction() == null) {
                throw new ProjectFundingGapException("direction",
                        "Event direction is required for event '" + event.eventId() + "'");
            }
            if (event.amount() == null) {
                throw new ProjectFundingGapException("amount",
                        "Event amount is required for event '" + event.eventId() + "'");
            }
            normalizeAmount("amount", event.amount(), true);
        }
    }

    private static BigDecimal normalizeAmount(String fieldName, BigDecimal amount, boolean mustBeStrictlyPositive) {
        String displayName = "openingFunds".equals(fieldName) ? "Opening funds" : fieldName;
        if (amount == null) {
            throw new ProjectFundingGapException(fieldName, displayName + ("Opening funds".equals(displayName) ? " are" : " is") + " required");
        }
        if (mustBeStrictlyPositive) {
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ProjectFundingGapException(fieldName,
                        displayName + " must be strictly positive (> 0), received: " + amount);
            }
        } else {
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                throw new ProjectFundingGapException(fieldName,
                        displayName + " must be non-negative (>= 0), received: " + amount);
            }
        }
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new ProjectFundingGapException(fieldName,
                    displayName + " must be representable exactly at two decimal places without rounding, received: " + amount);
        }
    }
}
