package com.app.sme_health_backend.recurring.calculator;

import com.app.sme_health_backend.recurring.model.RecurringFrequency;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Deterministic recurrence calculation engine with drift-free anchor preservation
 * for short months and leap years.
 */
public final class RecurrenceCalculator {

    private RecurrenceCalculator() {}

    /**
     * Calculates the next due date following {@code currentDueDate}, maintaining the original
     * {@code anchorDayOfMonth} and {@code anchorMonth} to prevent short-month date drift.
     *
     * Example: January 31 -> February 28 -> March 31 -> April 30 -> May 31.
     */
    public static LocalDate calculateNextDueDate(
            LocalDate currentDueDate,
            RecurringFrequency frequency,
            int anchorDayOfMonth,
            int anchorMonth
    ) {
        if (currentDueDate == null || frequency == null) {
            throw new IllegalArgumentException("currentDueDate and frequency are required");
        }
        if (anchorDayOfMonth < 1 || anchorDayOfMonth > 31) {
            throw new IllegalArgumentException("anchorDayOfMonth must be between 1 and 31");
        }
        if (anchorMonth < 1 || anchorMonth > 12) {
            throw new IllegalArgumentException("anchorMonth must be between 1 and 12");
        }

        switch (frequency) {
            case WEEKLY:
                return currentDueDate.plusWeeks(1);
            case BIWEEKLY:
                return currentDueDate.plusWeeks(2);
            case MONTHLY: {
                YearMonth nextYm = YearMonth.from(currentDueDate).plusMonths(1);
                int day = Math.min(anchorDayOfMonth, nextYm.lengthOfMonth());
                return nextYm.atDay(day);
            }
            case QUARTERLY: {
                YearMonth nextYm = YearMonth.from(currentDueDate).plusMonths(3);
                int day = Math.min(anchorDayOfMonth, nextYm.lengthOfMonth());
                return nextYm.atDay(day);
            }
            case ANNUALLY: {
                int nextYear = currentDueDate.getYear() + 1;
                YearMonth nextYm = YearMonth.of(nextYear, anchorMonth);
                int day = Math.min(anchorDayOfMonth, nextYm.lengthOfMonth());
                return nextYm.atDay(day);
            }
            default:
                throw new IllegalArgumentException("Unsupported frequency: " + frequency);
        }
    }

    /**
     * Previews future occurrences starting from {@code startDate} up to {@code maxCount} occurrences,
     * bounded by {@code endDate} if non-null.
     */
    public static List<LocalDate> previewSchedule(
            LocalDate startDate,
            LocalDate endDate,
            RecurringFrequency frequency,
            int anchorDayOfMonth,
            int anchorMonth,
            int maxCount
    ) {
        if (startDate == null || frequency == null) {
            throw new IllegalArgumentException("startDate and frequency are required");
        }
        if (maxCount <= 0) {
            return Collections.emptyList();
        }

        List<LocalDate> result = new ArrayList<>();
        LocalDate current = startDate;

        while (result.size() < maxCount) {
            if (endDate != null && current.isAfter(endDate)) {
                break;
            }
            result.add(current);
            current = calculateNextDueDate(current, frequency, anchorDayOfMonth, anchorMonth);
        }

        return Collections.unmodifiableList(result);
    }
}
