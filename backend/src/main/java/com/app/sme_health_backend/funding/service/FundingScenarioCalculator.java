package com.app.sme_health_backend.funding.service;

import com.app.sme_health_backend.funding.dto.*;
import com.app.sme_health_backend.funding.exception.FundingScenarioValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Pure, deterministic calculation engine for funding-gap and what-if financial scenarios.
 * <p>
 * Evaluates hypothetical dated cash inflows and outflows across a calendar horizon,
 * determining daily projected liquidity, deficit milestones, peak funding needs,
 * recovery dates, and baseline vs. alternative comparisons.
 * <p>
 * Invariants:
 * <ul>
 *   <li>Operates strictly with exact {@link BigDecimal} scale-2 arithmetic in PKR.</li>
 *   <li>Within-day events are netted into daily aggregates deterministically.</li>
 *   <li>Continuous daily curve is generated across all calendar days in the horizon.</li>
 *   <li>Scenario events are hypothetical simulations; no ledger postings occur.</li>
 * </ul>
 */
public class FundingScenarioCalculator {

    public static final String SUPPORTED_CURRENCY = "PKR";
    public static final BigDecimal ZERO_PKR = BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
    public static final int MAX_EVENTS_PER_SCENARIO = 100;

    /**
     * Calculates the daily funding curve and shortfall/recovery metrics for a single scenario.
     */
    public ScenarioResult calculateScenario(
            String scenarioName,
            BigDecimal openingLiquidity,
            LocalDate horizonStart,
            LocalDate horizonEnd,
            List<PlannedCashEventInput> events
    ) {
        if (scenarioName == null || scenarioName.isBlank()) {
            throw new FundingScenarioValidationException("scenarioName", "Scenario name is required");
        }
        if (openingLiquidity == null) {
            throw new FundingScenarioValidationException("openingLiquidity", "Opening liquidity amount is required");
        }
        openingLiquidity = normalizeAmount("openingLiquidity", openingLiquidity, false);

        if (horizonStart == null || horizonEnd == null) {
            throw new FundingScenarioValidationException("horizon", "Horizon start and end dates are required");
        }
        if (horizonStart.isAfter(horizonEnd)) {
            throw new FundingScenarioValidationException("horizon",
                    "Horizon start date (" + horizonStart + ") cannot be after horizon end date (" + horizonEnd + ")");
        }

        // Validate and aggregate events
        List<PlannedCashEventInput> rawEvents = events == null ? Collections.emptyList() : events;
        if (rawEvents.size() > MAX_EVENTS_PER_SCENARIO) {
            throw new FundingScenarioValidationException("events",
                    "Scenario cannot contain more than " + MAX_EVENTS_PER_SCENARIO + " events, received: " + rawEvents.size());
        }

        Set<String> seenEventIds = new HashSet<>();
        Map<LocalDate, List<PlannedCashEventDetail>> eventsByDate = new HashMap<>();
        Map<LocalDate, BigDecimal> dailyInflows = new HashMap<>();
        Map<LocalDate, BigDecimal> dailyOutflows = new HashMap<>();

        for (PlannedCashEventInput ev : rawEvents) {
            if (ev == null) {
                throw new FundingScenarioValidationException("events", "Event entry cannot be null");
            }
            if (ev.eventId() == null || ev.eventId().isBlank()) {
                throw new FundingScenarioValidationException("eventId", "Event ID is required and cannot be blank");
            }
            String trimmedId = ev.eventId().trim();
            if (!seenEventIds.add(trimmedId)) {
                throw new FundingScenarioValidationException("eventId", "Duplicate eventId: '" + ev.eventId() + "'");
            }

            LocalDate eventDate;
            try {
                eventDate = LocalDate.parse(ev.date());
            } catch (DateTimeParseException | NullPointerException ex) {
                throw new FundingScenarioValidationException("date",
                        "Invalid date for event '" + ev.eventId() + "'; expected YYYY-MM-DD");
            }

            if (eventDate.isBefore(horizonStart) || eventDate.isAfter(horizonEnd)) {
                throw new FundingScenarioValidationException("date",
                        "Event date (" + eventDate + ") for event '" + ev.eventId()
                                + "' is outside evaluation horizon [" + horizonStart + " to " + horizonEnd + "]");
            }

            if (ev.direction() == null) {
                throw new FundingScenarioValidationException("direction",
                        "Direction (INFLOW or OUTFLOW) is required for event '" + ev.eventId() + "'");
            }

            BigDecimal amount = normalizeAmount("amount", ev.amount(), true);

            PlannedCashEventDetail detail = new PlannedCashEventDetail(
                    trimmedId,
                    eventDate,
                    ev.direction().name(),
                    amount.toPlainString(),
                    ev.description(),
                    ev.category()
            );

            eventsByDate.computeIfAbsent(eventDate, k -> new ArrayList<>()).add(detail);

            if (ev.direction() == CashFlowDirection.INFLOW) {
                dailyInflows.merge(eventDate, amount, BigDecimal::add);
            } else {
                dailyOutflows.merge(eventDate, amount, BigDecimal::add);
            }
        }

        // Sort same-day events deterministically: INFLOW first, then OUTFLOW, then alphabetical eventId
        for (List<PlannedCashEventDetail> list : eventsByDate.values()) {
            list.sort(Comparator.comparing(PlannedCashEventDetail::direction)
                    .thenComparing(PlannedCashEventDetail::eventId));
        }

        // Build continuous daily timeline across all calendar days
        List<DailyFundingPosition> timeline = new ArrayList<>();
        BigDecimal currentPosition = openingLiquidity;
        BigDecimal totalInflows = ZERO_PKR;
        BigDecimal totalOutflows = ZERO_PKR;
        BigDecimal minPosition = null;
        LocalDate firstDeficitDate = null;
        LocalDate peakDeficitDate = null;
        int deficitDayCount = 0;

        for (LocalDate d = horizonStart; !d.isAfter(horizonEnd); d = d.plusDays(1)) {
            BigDecimal dayInflow = dailyInflows.getOrDefault(d, ZERO_PKR);
            BigDecimal dayOutflow = dailyOutflows.getOrDefault(d, ZERO_PKR);
            BigDecimal dayNet = dayInflow.subtract(dayOutflow);
            BigDecimal dayOpening = currentPosition;
            currentPosition = currentPosition.add(dayNet);

            totalInflows = totalInflows.add(dayInflow);
            totalOutflows = totalOutflows.add(dayOutflow);

            boolean isShortfall = currentPosition.compareTo(BigDecimal.ZERO) < 0;
            BigDecimal deficit = isShortfall ? currentPosition.negate() : ZERO_PKR;

            List<PlannedCashEventDetail> dayEvents = eventsByDate.getOrDefault(d, Collections.emptyList());

            timeline.add(new DailyFundingPosition(
                    d,
                    dayOpening.toPlainString(),
                    dayInflow.toPlainString(),
                    dayOutflow.toPlainString(),
                    dayNet.toPlainString(),
                    currentPosition.toPlainString(),
                    currentPosition.toPlainString(),
                    deficit.toPlainString(),
                    isShortfall,
                    Collections.unmodifiableList(dayEvents)
            ));

            if (minPosition == null || currentPosition.compareTo(minPosition) < 0) {
                minPosition = currentPosition;
            }

            if (isShortfall) {
                deficitDayCount++;
                if (firstDeficitDate == null) {
                    firstDeficitDate = d;
                }
            }
        }

        // Peak funding requirement = max(0, -minimum cumulative position)
        BigDecimal peakFundingRequirement;
        if (minPosition != null && minPosition.compareTo(BigDecimal.ZERO) < 0) {
            peakFundingRequirement = minPosition.negate();
            for (DailyFundingPosition pos : timeline) {
                if (new BigDecimal(pos.closingPosition()).compareTo(minPosition) == 0) {
                    peakDeficitDate = pos.date();
                    break;
                }
            }
        } else {
            peakFundingRequirement = ZERO_PKR;
            firstDeficitDate = null;
            peakDeficitDate = null;
        }

        // Recovery date: date liquidity returns to >= 0 and remains >= 0 through the horizon end
        LocalDate recoveryDate = null;
        if (firstDeficitDate != null && !timeline.isEmpty()) {
            DailyFundingPosition lastDay = timeline.get(timeline.size() - 1);
            BigDecimal lastClosing = new BigDecimal(lastDay.closingPosition());
            if (lastClosing.compareTo(BigDecimal.ZERO) >= 0) {
                int lastNegativeIdx = -1;
                for (int i = timeline.size() - 1; i >= 0; i--) {
                    if (new BigDecimal(timeline.get(i).closingPosition()).compareTo(BigDecimal.ZERO) < 0) {
                        lastNegativeIdx = i;
                        break;
                    }
                }
                if (lastNegativeIdx != -1 && lastNegativeIdx + 1 < timeline.size()) {
                    recoveryDate = timeline.get(lastNegativeIdx + 1).date();
                }
            }
        }

        BigDecimal netPlannedMovement = totalInflows.subtract(totalOutflows);
        BigDecimal closingLiquidity = timeline.isEmpty() ? openingLiquidity : currentPosition;

        return new ScenarioResult(
                scenarioName,
                totalInflows.toPlainString(),
                totalOutflows.toPlainString(),
                netPlannedMovement.toPlainString(),
                closingLiquidity.toPlainString(),
                peakFundingRequirement.toPlainString(),
                peakDeficitDate,
                firstDeficitDate,
                recoveryDate,
                deficitDayCount,
                firstDeficitDate != null,
                recoveryDate != null,
                Collections.unmodifiableList(timeline)
        );
    }

    /**
     * Compares an alternative scenario against a baseline scenario.
     */
    public ScenarioComparisonSummary compareScenarios(ScenarioResult baseline, ScenarioResult alternative) {
        if (baseline == null || alternative == null) {
            return null;
        }

        BigDecimal basePeak = new BigDecimal(baseline.peakFundingRequirement());
        BigDecimal altPeak = new BigDecimal(alternative.peakFundingRequirement());
        BigDecimal peakDiff = altPeak.subtract(basePeak);

        BigDecimal baseClosing = new BigDecimal(baseline.closingProjectedLiquidity());
        BigDecimal altClosing = new BigDecimal(alternative.closingProjectedLiquidity());
        BigDecimal closingDiff = altClosing.subtract(baseClosing);

        BigDecimal baseInflow = new BigDecimal(baseline.totalPlannedInflows());
        BigDecimal altInflow = new BigDecimal(alternative.totalPlannedInflows());
        BigDecimal inflowDiff = altInflow.subtract(baseInflow);

        BigDecimal baseOutflow = new BigDecimal(baseline.totalPlannedOutflows());
        BigDecimal altOutflow = new BigDecimal(alternative.totalPlannedOutflows());
        BigDecimal outflowDiff = altOutflow.subtract(baseOutflow);

        Integer firstDeficitDateChangeDays = null;
        if (baseline.firstDeficitDate() != null && alternative.firstDeficitDate() != null) {
            firstDeficitDateChangeDays = (int) ChronoUnit.DAYS.between(baseline.firstDeficitDate(), alternative.firstDeficitDate());
        }

        Integer recoveryDateChangeDays = null;
        if (baseline.recoveryDate() != null && alternative.recoveryDate() != null) {
            recoveryDateChangeDays = (int) ChronoUnit.DAYS.between(baseline.recoveryDate(), alternative.recoveryDate());
        }

        // Daily differences
        List<DailyScenarioDifference> dailyDiffs = new ArrayList<>();
        Map<LocalDate, DailyFundingPosition> altMap = new HashMap<>();
        for (DailyFundingPosition p : alternative.timeline()) {
            altMap.put(p.date(), p);
        }

        for (DailyFundingPosition basePos : baseline.timeline()) {
            DailyFundingPosition altPos = altMap.get(basePos.date());
            if (altPos != null) {
                BigDecimal bClose = new BigDecimal(basePos.closingPosition());
                BigDecimal aClose = new BigDecimal(altPos.closingPosition());
                BigDecimal posDiff = aClose.subtract(bClose);

                BigDecimal bDeficit = new BigDecimal(basePos.deficit());
                BigDecimal aDeficit = new BigDecimal(altPos.deficit());
                BigDecimal defDiff = aDeficit.subtract(bDeficit);

                dailyDiffs.add(new DailyScenarioDifference(
                        basePos.date(),
                        basePos.closingPosition(),
                        altPos.closingPosition(),
                        posDiff.toPlainString(),
                        basePos.deficit(),
                        altPos.deficit(),
                        defDiff.toPlainString()
                ));
            }
        }

        return new ScenarioComparisonSummary(
                baseline.scenarioName(),
                alternative.scenarioName(),
                peakDiff.toPlainString(),
                closingDiff.toPlainString(),
                inflowDiff.toPlainString(),
                outflowDiff.toPlainString(),
                firstDeficitDateChangeDays,
                recoveryDateChangeDays,
                Collections.unmodifiableList(dailyDiffs)
        );
    }

    private static BigDecimal normalizeAmount(String fieldName, BigDecimal amount, boolean mustBeStrictlyPositive) {
        if (amount == null) {
            throw new FundingScenarioValidationException(fieldName, fieldName + " is required");
        }
        if (mustBeStrictlyPositive) {
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new FundingScenarioValidationException(fieldName,
                        fieldName + " must be strictly positive (> 0), received: " + amount);
            }
        } else {
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                throw new FundingScenarioValidationException(fieldName,
                        fieldName + " must be non-negative (>= 0), received: " + amount);
            }
        }
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new FundingScenarioValidationException(fieldName,
                    fieldName + " must have at most 2 decimal places, received: " + amount);
        }
    }
}
