package com.app.sme_health_backend.recurring.calculator;

import com.app.sme_health_backend.recurring.model.RecurringFrequency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RecurrenceCalculator Unit Tests")
class RecurrenceCalculatorTest {

    @Test
    @DisplayName("Weekly frequency advances strictly by 7 days")
    void testWeeklyAdvancement() {
        LocalDate start = LocalDate.of(2026, 11, 2); // Monday
        int day = start.getDayOfMonth();
        int month = start.getMonthValue();

        LocalDate next1 = RecurrenceCalculator.calculateNextDueDate(start, RecurringFrequency.WEEKLY, day, month);
        assertEquals(LocalDate.of(2026, 11, 9), next1);

        LocalDate next2 = RecurrenceCalculator.calculateNextDueDate(next1, RecurringFrequency.WEEKLY, day, month);
        assertEquals(LocalDate.of(2026, 11, 16), next2);
    }

    @Test
    @DisplayName("Biweekly frequency advances strictly by 14 days")
    void testBiweeklyAdvancement() {
        LocalDate start = LocalDate.of(2026, 11, 2);
        int day = start.getDayOfMonth();
        int month = start.getMonthValue();

        LocalDate next1 = RecurrenceCalculator.calculateNextDueDate(start, RecurringFrequency.BIWEEKLY, day, month);
        assertEquals(LocalDate.of(2026, 11, 16), next1);

        LocalDate next2 = RecurrenceCalculator.calculateNextDueDate(next1, RecurringFrequency.BIWEEKLY, day, month);
        assertEquals(LocalDate.of(2026, 11, 30), next2);
    }

    @Test
    @DisplayName("Monthly frequency preserves day 31 anchor across short months without drift")
    void testMonthlyAnchorDay31Preservation() {
        // Non-leap year 2025: Jan 31 -> Feb 28 -> Mar 31 -> Apr 30 -> May 31
        LocalDate jan = LocalDate.of(2025, 1, 31);
        int dayAnchor = 31;
        int monthAnchor = 1;

        LocalDate feb = RecurrenceCalculator.calculateNextDueDate(jan, RecurringFrequency.MONTHLY, dayAnchor, monthAnchor);
        assertEquals(LocalDate.of(2025, 2, 28), feb);

        LocalDate mar = RecurrenceCalculator.calculateNextDueDate(feb, RecurringFrequency.MONTHLY, dayAnchor, monthAnchor);
        assertEquals(LocalDate.of(2025, 3, 31), mar, "Anchor day 31 must be restored in March, NOT drift to 28");

        LocalDate apr = RecurrenceCalculator.calculateNextDueDate(mar, RecurringFrequency.MONTHLY, dayAnchor, monthAnchor);
        assertEquals(LocalDate.of(2025, 4, 30), apr);

        LocalDate may = RecurrenceCalculator.calculateNextDueDate(apr, RecurringFrequency.MONTHLY, dayAnchor, monthAnchor);
        assertEquals(LocalDate.of(2025, 5, 31), may, "Anchor day 31 must be restored in May");
    }

    @Test
    @DisplayName("Monthly frequency preserves day 31 anchor in leap year (Feb 29)")
    void testMonthlyAnchorDay31LeapYear() {
        // Leap year 2024: Jan 31 -> Feb 29 -> Mar 31
        LocalDate jan = LocalDate.of(2024, 1, 31);
        int dayAnchor = 31;
        int monthAnchor = 1;

        LocalDate feb = RecurrenceCalculator.calculateNextDueDate(jan, RecurringFrequency.MONTHLY, dayAnchor, monthAnchor);
        assertEquals(LocalDate.of(2024, 2, 29), feb);

        LocalDate mar = RecurrenceCalculator.calculateNextDueDate(feb, RecurringFrequency.MONTHLY, dayAnchor, monthAnchor);
        assertEquals(LocalDate.of(2024, 3, 31), mar, "Anchor day 31 must be restored in March");
    }

    @Test
    @DisplayName("Monthly frequency preserves day 30 anchor across February")
    void testMonthlyAnchorDay30Preservation() {
        LocalDate jan = LocalDate.of(2025, 1, 30);
        int dayAnchor = 30;
        int monthAnchor = 1;

        LocalDate feb = RecurrenceCalculator.calculateNextDueDate(jan, RecurringFrequency.MONTHLY, dayAnchor, monthAnchor);
        assertEquals(LocalDate.of(2025, 2, 28), feb);

        LocalDate mar = RecurrenceCalculator.calculateNextDueDate(feb, RecurringFrequency.MONTHLY, dayAnchor, monthAnchor);
        assertEquals(LocalDate.of(2025, 3, 30), mar, "Anchor day 30 must be restored in March, NOT drift to 28");
    }

    @Test
    @DisplayName("Quarterly frequency advances by 3 months while preserving anchor")
    void testQuarterlyAdvancement() {
        // Jan 31 -> Apr 30 -> Jul 31 -> Oct 31 -> Jan 31
        LocalDate jan = LocalDate.of(2026, 1, 31);
        int dayAnchor = 31;
        int monthAnchor = 1;

        LocalDate q2 = RecurrenceCalculator.calculateNextDueDate(jan, RecurringFrequency.QUARTERLY, dayAnchor, monthAnchor);
        assertEquals(LocalDate.of(2026, 4, 30), q2);

        LocalDate q3 = RecurrenceCalculator.calculateNextDueDate(q2, RecurringFrequency.QUARTERLY, dayAnchor, monthAnchor);
        assertEquals(LocalDate.of(2026, 7, 31), q3, "Anchor 31 restored in July");

        LocalDate q4 = RecurrenceCalculator.calculateNextDueDate(q3, RecurringFrequency.QUARTERLY, dayAnchor, monthAnchor);
        assertEquals(LocalDate.of(2026, 10, 31), q4);

        LocalDate nextYearQ1 = RecurrenceCalculator.calculateNextDueDate(q4, RecurringFrequency.QUARTERLY, dayAnchor, monthAnchor);
        assertEquals(LocalDate.of(2027, 1, 31), nextYearQ1);
    }

    @Test
    @DisplayName("Annually frequency handles leap day Feb 29 anchor correctly")
    void testAnnuallyLeapDayAnchor() {
        // Feb 29, 2024 -> Feb 28, 2025 -> Feb 28, 2026 -> Feb 28, 2027 -> Feb 29, 2028
        LocalDate leapAnchor = LocalDate.of(2024, 2, 29);
        int dayAnchor = 29;
        int monthAnchor = 2;

        LocalDate y2025 = RecurrenceCalculator.calculateNextDueDate(leapAnchor, RecurringFrequency.ANNUALLY, dayAnchor, monthAnchor);
        assertEquals(LocalDate.of(2025, 2, 28), y2025);

        LocalDate y2026 = RecurrenceCalculator.calculateNextDueDate(y2025, RecurringFrequency.ANNUALLY, dayAnchor, monthAnchor);
        assertEquals(LocalDate.of(2026, 2, 28), y2026);

        LocalDate y2027 = RecurrenceCalculator.calculateNextDueDate(y2026, RecurringFrequency.ANNUALLY, dayAnchor, monthAnchor);
        assertEquals(LocalDate.of(2027, 2, 28), y2027);

        LocalDate y2028 = RecurrenceCalculator.calculateNextDueDate(y2027, RecurringFrequency.ANNUALLY, dayAnchor, monthAnchor);
        assertEquals(LocalDate.of(2028, 2, 29), y2028, "Leap day Feb 29 restored in next leap year");
    }

    @Test
    @DisplayName("Preview schedule generates requested count without drift")
    void testPreviewSchedule() {
        LocalDate start = LocalDate.of(2026, 1, 31);
        List<LocalDate> preview = RecurrenceCalculator.previewSchedule(
                start, null, RecurringFrequency.MONTHLY, 31, 1, 6
        );

        assertEquals(6, preview.size());
        assertEquals(LocalDate.of(2026, 1, 31), preview.get(0));
        assertEquals(LocalDate.of(2026, 2, 28), preview.get(1));
        assertEquals(LocalDate.of(2026, 3, 31), preview.get(2));
        assertEquals(LocalDate.of(2026, 4, 30), preview.get(3));
        assertEquals(LocalDate.of(2026, 5, 31), preview.get(4));
        assertEquals(LocalDate.of(2026, 6, 30), preview.get(5));
    }

    @Test
    @DisplayName("Preview schedule respects end date boundary strictly")
    void testPreviewScheduleEndDate() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate endDate = LocalDate.of(2026, 3, 15);
        List<LocalDate> preview = RecurrenceCalculator.previewSchedule(
                start, endDate, RecurringFrequency.MONTHLY, 1, 1, 10
        );

        assertEquals(3, preview.size());
        assertEquals(LocalDate.of(2026, 1, 1), preview.get(0));
        assertEquals(LocalDate.of(2026, 2, 1), preview.get(1));
        assertEquals(LocalDate.of(2026, 3, 1), preview.get(2));
    }
}
