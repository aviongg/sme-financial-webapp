package com.app.sme_health_backend.obligation.aging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ObligationAgingCalculator Tests")
class ObligationAgingCalculatorTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 10, 17);
    private static final String CURRENCY_PKR = "PKR";

    private BigDecimal pkr(String amount) {
        return new BigDecimal(amount);
    }

    @Test
    @DisplayName("Reference fixture: validates R1, R2, R3, P1 and directional totals exactly as specified")
    void testReferenceFixture() {
        // Receivable R1:
        // issued 2026-09-01, due 2026-09-17, original 100000.00
        // reduction 25000.00 on 2026-10-01
        // reduction 10000.00 on 2026-10-20 (future as of 2026-10-17)
        ObligationHistory r1 = new ObligationHistory(
                "R1",
                ObligationDirection.RECEIVABLE,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 17),
                pkr("100000.00"),
                null,
                List.of(
                        new SettlementAdjustment("ADJ-R1-1", LocalDate.of(2026, 10, 1), SettlementAdjustmentType.REDUCE_OUTSTANDING, pkr("25000.00")),
                        new SettlementAdjustment("ADJ-R1-2", LocalDate.of(2026, 10, 20), SettlementAdjustmentType.REDUCE_OUTSTANDING, pkr("10000.00"))
                )
        );

        // Receivable R2:
        // issued 2026-10-01, due 2026-10-17, original 20000.00, no adjustments
        ObligationHistory r2 = new ObligationHistory(
                "R2",
                ObligationDirection.RECEIVABLE,
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 17),
                pkr("20000.00"),
                null,
                List.of()
        );

        // Receivable R3:
        // issued 2026-10-01, due date missing, original 5000.00
        ObligationHistory r3 = new ObligationHistory(
                "R3",
                ObligationDirection.RECEIVABLE,
                LocalDate.of(2026, 10, 1),
                null,
                pkr("5000.00"),
                null,
                List.of()
        );

        // Payable P1:
        // issued 2026-10-01, due 2026-10-18, original 40000.00
        ObligationHistory p1 = new ObligationHistory(
                "P1",
                ObligationDirection.PAYABLE,
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 18),
                pkr("40000.00"),
                null,
                List.of()
        );

        ObligationAgingResult result = ObligationAgingCalculator.calculate(
                AS_OF,
                CURRENCY_PKR,
                List.of(r1, r2, r3, p1)
        );

        // Date and Currency
        assertEquals(AS_OF, result.asOfDate());
        assertEquals(CURRENCY_PKR, result.currency());

        // Receivables Direction Totals
        assertEquals(pkr("100000.00"), result.receivableTotalOutstanding());
        assertEquals(pkr("75000.00"), result.receivableOverdueOutstanding());
        assertEquals(3, result.receivables().totalActiveCount());
        assertEquals(1, result.receivables().overdueCount());

        // Payables Direction Totals
        assertEquals(pkr("40000.00"), result.payableTotalOutstanding());
        assertEquals(pkr("0.00"), result.payableOverdueOutstanding());
        assertEquals(1, result.payables().totalActiveCount());
        assertEquals(0, result.payables().overdueCount());

        // Detail checks
        // R1: 75,000.00 outstanding, 30 days overdue, OVERDUE_1_30
        ObligationAgingDetail detailR1 = result.details().stream()
                .filter(d -> "R1".equals(d.obligationId()))
                .findFirst().orElseThrow();
        assertEquals(pkr("75000.00"), detailR1.asOfOutstanding());
        assertEquals(30, detailR1.daysOverdue());
        assertEquals(AgingBucket.OVERDUE_1_30, detailR1.assignedBucket());
        assertTrue(detailR1.isOverdue());
        assertTrue(detailR1.isActive());
        assertNull(detailR1.exclusionStatus());

        // R2: 20,000.00 outstanding, DUE_TODAY, not overdue
        ObligationAgingDetail detailR2 = result.details().stream()
                .filter(d -> "R2".equals(d.obligationId()))
                .findFirst().orElseThrow();
        assertEquals(pkr("20000.00"), detailR2.asOfOutstanding());
        assertNull(detailR2.daysOverdue());
        assertEquals(AgingBucket.DUE_TODAY, detailR2.assignedBucket());
        assertFalse(detailR2.isOverdue());
        assertTrue(detailR2.isActive());

        // R3: 5,000.00 outstanding, UNKNOWN_DUE_DATE, not overdue
        ObligationAgingDetail detailR3 = result.details().stream()
                .filter(d -> "R3".equals(d.obligationId()))
                .findFirst().orElseThrow();
        assertEquals(pkr("5000.00"), detailR3.asOfOutstanding());
        assertNull(detailR3.daysOverdue());
        assertEquals(AgingBucket.UNKNOWN_DUE_DATE, detailR3.assignedBucket());
        assertFalse(detailR3.isOverdue());
        assertTrue(detailR3.isActive());

        // P1: 40,000.00 outstanding, NOT_YET_DUE, not overdue
        ObligationAgingDetail detailP1 = result.details().stream()
                .filter(d -> "P1".equals(d.obligationId()))
                .findFirst().orElseThrow();
        assertEquals(pkr("40000.00"), detailP1.asOfOutstanding());
        assertNull(detailP1.daysOverdue());
        assertEquals(AgingBucket.NOT_YET_DUE, detailP1.assignedBucket());
        assertFalse(detailP1.isOverdue());
        assertTrue(detailP1.isActive());

        // Bucket distribution for receivables
        assertEquals(pkr("5000.00"), result.receivables().getBucket(AgingBucket.UNKNOWN_DUE_DATE).totalOutstanding());
        assertEquals(1, result.receivables().getBucket(AgingBucket.UNKNOWN_DUE_DATE).count());
        assertEquals(pkr("20000.00"), result.receivables().getBucket(AgingBucket.DUE_TODAY).totalOutstanding());
        assertEquals(1, result.receivables().getBucket(AgingBucket.DUE_TODAY).count());
        assertEquals(pkr("75000.00"), result.receivables().getBucket(AgingBucket.OVERDUE_1_30).totalOutstanding());
        assertEquals(1, result.receivables().getBucket(AgingBucket.OVERDUE_1_30).count());
        assertEquals(pkr("0.00"), result.receivables().getBucket(AgingBucket.NOT_YET_DUE).totalOutstanding());
        assertEquals(0, result.receivables().getBucket(AgingBucket.NOT_YET_DUE).count());

        // Bucket distribution for payables
        assertEquals(pkr("40000.00"), result.payables().getBucket(AgingBucket.NOT_YET_DUE).totalOutstanding());
        assertEquals(1, result.payables().getBucket(AgingBucket.NOT_YET_DUE).count());
        assertEquals(pkr("0.00"), result.payables().getBucket(AgingBucket.DUE_TODAY).totalOutstanding());
        assertEquals(0, result.payables().getBucket(AgingBucket.DUE_TODAY).count());
    }

    @Test
    @DisplayName("All overdue bucket boundaries: 1, 30, 31, 60, 61, 90, 91 days tested precisely")
    void testAllBucketBoundaries() {
        // asOfDate = 2026-10-17
        // 1 day overdue: due 2026-10-16 -> OVERDUE_1_30
        // 30 days overdue: due 2026-09-17 -> OVERDUE_1_30
        // 31 days overdue: due 2026-09-16 -> OVERDUE_31_60
        // 60 days overdue: due 2026-08-18 -> OVERDUE_31_60
        // 61 days overdue: due 2026-08-17 -> OVERDUE_61_90
        // 90 days overdue: due 2026-07-19 -> OVERDUE_61_90
        // 91 days overdue: due 2026-07-18 -> OVERDUE_91_PLUS

        List<ObligationHistory> obligations = List.of(
                createObligation("B-1", AS_OF.minusDays(10), AS_OF.minusDays(1), pkr("100.00")),
                createObligation("B-30", AS_OF.minusDays(40), AS_OF.minusDays(30), pkr("100.00")),
                createObligation("B-31", AS_OF.minusDays(40), AS_OF.minusDays(31), pkr("100.00")),
                createObligation("B-60", AS_OF.minusDays(70), AS_OF.minusDays(60), pkr("100.00")),
                createObligation("B-61", AS_OF.minusDays(70), AS_OF.minusDays(61), pkr("100.00")),
                createObligation("B-90", AS_OF.minusDays(100), AS_OF.minusDays(90), pkr("100.00")),
                createObligation("B-91", AS_OF.minusDays(100), AS_OF.minusDays(91), pkr("100.00"))
        );

        ObligationAgingResult result = ObligationAgingCalculator.calculate(AS_OF, CURRENCY_PKR, obligations);

        assertEquals(AgingBucket.OVERDUE_1_30, findDetail(result, "B-1").assignedBucket());
        assertEquals(1, findDetail(result, "B-1").daysOverdue());

        assertEquals(AgingBucket.OVERDUE_1_30, findDetail(result, "B-30").assignedBucket());
        assertEquals(30, findDetail(result, "B-30").daysOverdue());

        assertEquals(AgingBucket.OVERDUE_31_60, findDetail(result, "B-31").assignedBucket());
        assertEquals(31, findDetail(result, "B-31").daysOverdue());

        assertEquals(AgingBucket.OVERDUE_31_60, findDetail(result, "B-60").assignedBucket());
        assertEquals(60, findDetail(result, "B-60").daysOverdue());

        assertEquals(AgingBucket.OVERDUE_61_90, findDetail(result, "B-61").assignedBucket());
        assertEquals(61, findDetail(result, "B-61").daysOverdue());

        assertEquals(AgingBucket.OVERDUE_61_90, findDetail(result, "B-90").assignedBucket());
        assertEquals(90, findDetail(result, "B-90").daysOverdue());

        assertEquals(AgingBucket.OVERDUE_91_PLUS, findDetail(result, "B-91").assignedBucket());
        assertEquals(91, findDetail(result, "B-91").daysOverdue());

        assertEquals(2, result.receivables().getBucket(AgingBucket.OVERDUE_1_30).count());
        assertEquals(pkr("200.00"), result.receivables().getBucket(AgingBucket.OVERDUE_1_30).totalOutstanding());

        assertEquals(2, result.receivables().getBucket(AgingBucket.OVERDUE_31_60).count());
        assertEquals(pkr("200.00"), result.receivables().getBucket(AgingBucket.OVERDUE_31_60).totalOutstanding());

        assertEquals(2, result.receivables().getBucket(AgingBucket.OVERDUE_61_90).count());
        assertEquals(pkr("200.00"), result.receivables().getBucket(AgingBucket.OVERDUE_61_90).totalOutstanding());

        assertEquals(1, result.receivables().getBucket(AgingBucket.OVERDUE_91_PLUS).count());
        assertEquals(pkr("100.00"), result.receivables().getBucket(AgingBucket.OVERDUE_91_PLUS).totalOutstanding());

        assertEquals(pkr("700.00"), result.receivableTotalOutstanding());
        assertEquals(pkr("700.00"), result.receivableOverdueOutstanding());
    }

    @Test
    @DisplayName("Due today vs future due date separation")
    void testDueTodayVersusFutureDue() {
        ObligationHistory dueToday = createObligation("TODAY", AS_OF.minusDays(5), AS_OF, pkr("500.00"));
        ObligationHistory dueTomorrow = createObligation("TOMORROW", AS_OF.minusDays(5), AS_OF.plusDays(1), pkr("700.00"));

        ObligationAgingResult result = ObligationAgingCalculator.calculate(AS_OF, CURRENCY_PKR, List.of(dueToday, dueTomorrow));

        ObligationAgingDetail detailToday = findDetail(result, "TODAY");
        assertEquals(AgingBucket.DUE_TODAY, detailToday.assignedBucket());
        assertNull(detailToday.daysOverdue());
        assertFalse(detailToday.isOverdue());

        ObligationAgingDetail detailTomorrow = findDetail(result, "TOMORROW");
        assertEquals(AgingBucket.NOT_YET_DUE, detailTomorrow.assignedBucket());
        assertNull(detailTomorrow.daysOverdue());
        assertFalse(detailTomorrow.isOverdue());

        assertEquals(pkr("1200.00"), result.receivableTotalOutstanding());
        assertEquals(pkr("0.00"), result.receivableOverdueOutstanding());
    }

    @Test
    @DisplayName("Missing due date is categorized as UNKNOWN_DUE_DATE and excluded from overdue")
    void testMissingDueDate() {
        ObligationHistory noDue = new ObligationHistory(
                "NO-DUE",
                ObligationDirection.RECEIVABLE,
                AS_OF.minusDays(15),
                null,
                pkr("3000.00"),
                null,
                List.of()
        );

        ObligationAgingResult result = ObligationAgingCalculator.calculate(AS_OF, CURRENCY_PKR, List.of(noDue));

        ObligationAgingDetail detail = findDetail(result, "NO-DUE");
        assertEquals(AgingBucket.UNKNOWN_DUE_DATE, detail.assignedBucket());
        assertNull(detail.daysOverdue());
        assertFalse(detail.isOverdue());

        assertEquals(pkr("3000.00"), result.receivableTotalOutstanding());
        assertEquals(pkr("0.00"), result.receivableOverdueOutstanding());
    }

    @Test
    @DisplayName("Historical as-of date before and after settlements")
    void testHistoricalAsOfBeforeAndAfterSettlement() {
        LocalDate issue = LocalDate.of(2026, 8, 1);
        LocalDate due = LocalDate.of(2026, 8, 15);
        LocalDate payDate = LocalDate.of(2026, 9, 1);

        ObligationHistory history = new ObligationHistory(
                "HIST-1",
                ObligationDirection.RECEIVABLE,
                issue,
                due,
                pkr("10000.00"),
                null,
                List.of(new SettlementAdjustment("ADJ-1", payDate, SettlementAdjustmentType.REDUCE_OUTSTANDING, pkr("4000.00")))
        );

        // Before settlement (2026-08-20)
        ObligationAgingResult resultBefore = ObligationAgingCalculator.calculate(
                LocalDate.of(2026, 8, 20), CURRENCY_PKR, List.of(history));
        assertEquals(pkr("10000.00"), findDetail(resultBefore, "HIST-1").asOfOutstanding());
        assertEquals(5, findDetail(resultBefore, "HIST-1").daysOverdue());

        // On settlement date (2026-09-01)
        ObligationAgingResult resultOn = ObligationAgingCalculator.calculate(
                payDate, CURRENCY_PKR, List.of(history));
        assertEquals(pkr("6000.00"), findDetail(resultOn, "HIST-1").asOfOutstanding());
        assertEquals(17, findDetail(resultOn, "HIST-1").daysOverdue());

        // Well after settlement (2026-10-17)
        ObligationAgingResult resultAfter = ObligationAgingCalculator.calculate(
                AS_OF, CURRENCY_PKR, List.of(history));
        assertEquals(pkr("6000.00"), findDetail(resultAfter, "HIST-1").asOfOutstanding());
        assertEquals(63, findDetail(resultAfter, "HIST-1").daysOverdue());
        assertEquals(AgingBucket.OVERDUE_61_90, findDetail(resultAfter, "HIST-1").assignedBucket());
    }

    @Test
    @DisplayName("Restoration of outstanding balance increases outstanding up to original amount")
    void testRestorationOfOutstanding() {
        ObligationHistory history = new ObligationHistory(
                "RESTORE-1",
                ObligationDirection.RECEIVABLE,
                AS_OF.minusDays(30),
                AS_OF.minusDays(10),
                pkr("10000.00"),
                null,
                List.of(
                        new SettlementAdjustment("A1", AS_OF.minusDays(20), SettlementAdjustmentType.REDUCE_OUTSTANDING, pkr("8000.00")),
                        new SettlementAdjustment("A2", AS_OF.minusDays(10), SettlementAdjustmentType.RESTORE_OUTSTANDING, pkr("3000.00"))
                )
        );

        ObligationAgingResult result = ObligationAgingCalculator.calculate(AS_OF, CURRENCY_PKR, List.of(history));

        // Initial 10,000 - 8,000 = 2,000; + 3,000 = 5,000.00
        assertEquals(pkr("5000.00"), findDetail(result, "RESTORE-1").asOfOutstanding());
        assertEquals(10, findDetail(result, "RESTORE-1").daysOverdue());
        assertEquals(AgingBucket.OVERDUE_1_30, findDetail(result, "RESTORE-1").assignedBucket());
    }

    @Test
    @DisplayName("Invalid history: running outstanding cannot drop below zero")
    void testInvalidHistoryNegativeBalanceRejected() {
        ObligationHistory history = new ObligationHistory(
                "NEG-1",
                ObligationDirection.RECEIVABLE,
                AS_OF.minusDays(10),
                AS_OF.minusDays(5),
                pkr("5000.00"),
                null,
                List.of(new SettlementAdjustment("A1", AS_OF.minusDays(2), SettlementAdjustmentType.REDUCE_OUTSTANDING, pkr("5000.01")))
        );

        ObligationAgingException ex = assertThrows(ObligationAgingException.class,
                () -> ObligationAgingCalculator.calculate(AS_OF, CURRENCY_PKR, List.of(history)));
        assertEquals("adjustments", ex.getField());
        assertTrue(ex.getMessage().contains("negative"));
    }

    @Test
    @DisplayName("Invalid history: running outstanding cannot exceed original amount")
    void testInvalidHistoryExceedsOriginalAmountRejected() {
        ObligationHistory history = new ObligationHistory(
                "EXCESS-1",
                ObligationDirection.RECEIVABLE,
                AS_OF.minusDays(10),
                AS_OF.minusDays(5),
                pkr("5000.00"),
                null,
                List.of(
                        new SettlementAdjustment("A1", AS_OF.minusDays(4), SettlementAdjustmentType.REDUCE_OUTSTANDING, pkr("2000.00")),
                        new SettlementAdjustment("A2", AS_OF.minusDays(2), SettlementAdjustmentType.RESTORE_OUTSTANDING, pkr("2000.01"))
                )
        );

        ObligationAgingException ex = assertThrows(ObligationAgingException.class,
                () -> ObligationAgingCalculator.calculate(AS_OF, CURRENCY_PKR, List.of(history)));
        assertEquals("adjustments", ex.getField());
        assertTrue(ex.getMessage().contains("exceeded original amount"));
    }

    @Test
    @DisplayName("Invalid future adjustment after asOfDate is still rejected (full history validation)")
    void testInvalidFutureAdjustmentRejectedEvenWhenAsOfValid() {
        ObligationHistory history = new ObligationHistory(
                "FUTURE-INVALID",
                ObligationDirection.RECEIVABLE,
                AS_OF.minusDays(10),
                AS_OF.minusDays(5),
                pkr("5000.00"),
                null,
                List.of(
                        // Valid as of AS_OF: 5,000 - 1,000 = 4,000
                        new SettlementAdjustment("A1", AS_OF.minusDays(2), SettlementAdjustmentType.REDUCE_OUTSTANDING, pkr("1000.00")),
                        // Invalid future adjustment after AS_OF: 4,000 - 4,500 = -500
                        new SettlementAdjustment("A2", AS_OF.plusDays(5), SettlementAdjustmentType.REDUCE_OUTSTANDING, pkr("4500.00"))
                )
        );

        ObligationAgingException ex = assertThrows(ObligationAgingException.class,
                () -> ObligationAgingCalculator.calculate(AS_OF, CURRENCY_PKR, List.of(history)));
        assertEquals("adjustments", ex.getField());
        assertTrue(ex.getMessage().contains("negative"));
    }

    @Test
    @DisplayName("Same-day netting and shuffled input order produce identical deterministic results")
    void testSameDayNettingAndShuffledOrder() {
        LocalDate adjDate = AS_OF.minusDays(5);

        List<SettlementAdjustment> adjustments = new ArrayList<>(List.of(
                new SettlementAdjustment("ADJ-1", adjDate, SettlementAdjustmentType.REDUCE_OUTSTANDING, pkr("4000.00")),
                new SettlementAdjustment("ADJ-2", adjDate, SettlementAdjustmentType.RESTORE_OUTSTANDING, pkr("1500.00")),
                new SettlementAdjustment("ADJ-3", adjDate, SettlementAdjustmentType.REDUCE_OUTSTANDING, pkr("500.00")),
                new SettlementAdjustment("ADJ-4", adjDate, SettlementAdjustmentType.RESTORE_OUTSTANDING, pkr("2000.00"))
        ));

        // Net change on adjDate = -4000 + 1500 - 500 + 2000 = -1000.00
        // Expected balance = 10,000 - 1000 = 9,000.00
        ObligationHistory h1 = new ObligationHistory("OBL-1", ObligationDirection.PAYABLE, AS_OF.minusDays(20), AS_OF.minusDays(10), pkr("10000.00"), null, adjustments);
        ObligationAgingResult res1 = ObligationAgingCalculator.calculate(AS_OF, CURRENCY_PKR, List.of(h1));
        assertEquals(pkr("9000.00"), findDetail(res1, "OBL-1").asOfOutstanding());

        // Reverse / shuffle adjustments
        Collections.reverse(adjustments);
        ObligationHistory h2 = new ObligationHistory("OBL-1", ObligationDirection.PAYABLE, AS_OF.minusDays(20), AS_OF.minusDays(10), pkr("10000.00"), null, adjustments);
        ObligationAgingResult res2 = ObligationAgingCalculator.calculate(AS_OF, CURRENCY_PKR, List.of(h2));
        assertEquals(pkr("9000.00"), findDetail(res2, "OBL-1").asOfOutstanding());
    }

    @Test
    @DisplayName("Future issue date and effective void dates correctly set exclusion status")
    void testFutureIssueAndVoidDates() {
        // Issue date in the future (> asOfDate)
        ObligationHistory futureIssued = new ObligationHistory(
                "FUTURE-ISSUE",
                ObligationDirection.RECEIVABLE,
                AS_OF.plusDays(2),
                AS_OF.plusDays(10),
                pkr("5000.00"),
                null,
                List.of()
        );

        // Voided before/on asOfDate (voidDate <= asOfDate)
        ObligationHistory voidedPast = new ObligationHistory(
                "VOIDED-PAST",
                ObligationDirection.RECEIVABLE,
                AS_OF.minusDays(20),
                AS_OF.minusDays(5),
                pkr("8000.00"),
                AS_OF.minusDays(2),
                List.of()
        );

        // Voided after asOfDate (voidDate > asOfDate) -> included normally as of asOfDate!
        ObligationHistory voidedFuture = new ObligationHistory(
                "VOIDED-FUTURE",
                ObligationDirection.RECEIVABLE,
                AS_OF.minusDays(20),
                AS_OF.minusDays(5),
                pkr("12000.00"),
                AS_OF.plusDays(5),
                List.of()
        );

        ObligationAgingResult result = ObligationAgingCalculator.calculate(
                AS_OF,
                CURRENCY_PKR,
                List.of(futureIssued, voidedPast, voidedFuture)
        );

        ObligationAgingDetail detailFuture = findDetail(result, "FUTURE-ISSUE");
        assertEquals(ObligationExclusionStatus.NOT_YET_ISSUED, detailFuture.exclusionStatus());
        assertFalse(detailFuture.isActive());
        assertNull(detailFuture.assignedBucket());

        ObligationAgingDetail detailVoided = findDetail(result, "VOIDED-PAST");
        assertEquals(ObligationExclusionStatus.VOIDED, detailVoided.exclusionStatus());
        assertFalse(detailVoided.isActive());
        assertNull(detailVoided.assignedBucket());

        ObligationAgingDetail detailVoidFuture = findDetail(result, "VOIDED-FUTURE");
        assertNull(detailVoidFuture.exclusionStatus());
        assertTrue(detailVoidFuture.isActive());
        assertEquals(AgingBucket.OVERDUE_1_30, detailVoidFuture.assignedBucket());
        assertEquals(5, detailVoidFuture.daysOverdue());
        assertEquals(pkr("12000.00"), detailVoidFuture.asOfOutstanding());

        // Only voidedFuture is active
        assertEquals(pkr("12000.00"), result.receivableTotalOutstanding());
        assertEquals(1, result.receivables().totalActiveCount());
        assertEquals(1, result.receivables().notYetIssuedCount());
        assertEquals(1, result.receivables().voidedCount());
    }

    @Test
    @DisplayName("Fully settled obligations are categorized as SETTLED and excluded from positive aging totals")
    void testFullySettledObligations() {
        ObligationHistory settled = new ObligationHistory(
                "SETTLED-1",
                ObligationDirection.RECEIVABLE,
                AS_OF.minusDays(20),
                AS_OF.minusDays(5),
                pkr("5000.00"),
                null,
                List.of(new SettlementAdjustment("A1", AS_OF.minusDays(10), SettlementAdjustmentType.REDUCE_OUTSTANDING, pkr("5000.00")))
        );

        ObligationAgingResult result = ObligationAgingCalculator.calculate(AS_OF, CURRENCY_PKR, List.of(settled));

        ObligationAgingDetail detail = findDetail(result, "SETTLED-1");
        assertEquals(pkr("0.00"), detail.asOfOutstanding());
        assertEquals(ObligationExclusionStatus.SETTLED, detail.exclusionStatus());
        assertFalse(detail.isActive());
        assertNull(detail.assignedBucket());

        assertEquals(pkr("0.00"), result.receivableTotalOutstanding());
        assertEquals(pkr("0.00"), result.receivableOverdueOutstanding());
        assertEquals(0, result.receivables().totalActiveCount());
        assertEquals(1, result.receivables().settledCount());
    }

    @Test
    @DisplayName("Leap year and calendar boundary calculations")
    void testLeapYearAndCalendarBoundaries() {
        // Leap year 2024: 2024-02-28 to 2024-03-01 is 2 days
        LocalDate leapAsOf = LocalDate.of(2024, 3, 1);
        ObligationHistory leapObligation = createObligation(
                "LEAP-1",
                LocalDate.of(2024, 2, 1),
                LocalDate.of(2024, 2, 28),
                pkr("1000.00")
        );

        ObligationAgingResult result = ObligationAgingCalculator.calculate(leapAsOf, CURRENCY_PKR, List.of(leapObligation));

        ObligationAgingDetail detail = findDetail(result, "LEAP-1");
        // Feb 28 -> Feb 29 (1 day) -> Mar 1 (2 days)
        assertEquals(2, detail.daysOverdue());
        assertEquals(AgingBucket.OVERDUE_1_30, detail.assignedBucket());

        // Year boundary: 2025-12-31 to 2026-01-01 is 1 day overdue
        LocalDate yearBoundaryAsOf = LocalDate.of(2026, 1, 1);
        ObligationHistory yearEndObligation = createObligation(
                "YEAR-1",
                LocalDate.of(2025, 12, 1),
                LocalDate.of(2025, 12, 31),
                pkr("2000.00")
        );
        ObligationAgingResult yearResult = ObligationAgingCalculator.calculate(yearBoundaryAsOf, CURRENCY_PKR, List.of(yearEndObligation));
        assertEquals(1, findDetail(yearResult, "YEAR-1").daysOverdue());
    }

    @Test
    @DisplayName("Input validations: rejects duplicate IDs, invalid dates, negative amounts, non-PKR currency")
    void testInputValidations() {
        // Null asOfDate
        assertThrows(ObligationAgingException.class,
                () -> ObligationAgingCalculator.calculate(null, CURRENCY_PKR, List.of()));

        // Null / Unsupported currency
        assertThrows(ObligationAgingException.class,
                () -> ObligationAgingCalculator.calculate(AS_OF, null, List.of()));
        assertThrows(ObligationAgingException.class,
                () -> ObligationAgingCalculator.calculate(AS_OF, "USD", List.of()));

        // Null obligations collection
        assertThrows(ObligationAgingException.class,
                () -> ObligationAgingCalculator.calculate(AS_OF, CURRENCY_PKR, null));

        // Duplicate obligation IDs
        ObligationHistory o1 = createObligation("DUP-1", AS_OF.minusDays(5), AS_OF, pkr("100.00"));
        ObligationHistory o2 = createObligation("DUP-1", AS_OF.minusDays(5), AS_OF, pkr("200.00"));
        ObligationAgingException exDup = assertThrows(ObligationAgingException.class,
                () -> ObligationAgingCalculator.calculate(AS_OF, CURRENCY_PKR, List.of(o1, o2)));
        assertEquals("obligationId", exDup.getField());

        // Duplicate adjustment IDs within obligation
        ObligationHistory dupAdj = new ObligationHistory(
                "OBL-ADJ-DUP",
                ObligationDirection.RECEIVABLE,
                AS_OF.minusDays(10),
                AS_OF,
                pkr("1000.00"),
                null,
                List.of(
                        new SettlementAdjustment("SAME-KEY", AS_OF.minusDays(5), SettlementAdjustmentType.REDUCE_OUTSTANDING, pkr("100.00")),
                        new SettlementAdjustment("SAME-KEY", AS_OF.minusDays(4), SettlementAdjustmentType.REDUCE_OUTSTANDING, pkr("100.00"))
                )
        );
        assertThrows(ObligationAgingException.class,
                () -> ObligationAgingCalculator.calculate(AS_OF, CURRENCY_PKR, List.of(dupAdj)));

        // Due date before issue date
        ObligationHistory dueBeforeIssue = new ObligationHistory(
                "BAD-DUE",
                ObligationDirection.RECEIVABLE,
                AS_OF.minusDays(5),
                AS_OF.minusDays(10),
                pkr("1000.00"),
                null,
                List.of()
        );
        assertThrows(ObligationAgingException.class,
                () -> ObligationAgingCalculator.calculate(AS_OF, CURRENCY_PKR, List.of(dueBeforeIssue)));

        // Void date before issue date
        ObligationHistory voidBeforeIssue = new ObligationHistory(
                "BAD-VOID",
                ObligationDirection.RECEIVABLE,
                AS_OF.minusDays(5),
                AS_OF,
                pkr("1000.00"),
                AS_OF.minusDays(10),
                List.of()
        );
        assertThrows(ObligationAgingException.class,
                () -> ObligationAgingCalculator.calculate(AS_OF, CURRENCY_PKR, List.of(voidBeforeIssue)));

        // Adjustment date before issue date
        ObligationHistory adjBeforeIssue = new ObligationHistory(
                "BAD-ADJ-DATE",
                ObligationDirection.RECEIVABLE,
                AS_OF.minusDays(5),
                AS_OF,
                pkr("1000.00"),
                null,
                List.of(new SettlementAdjustment("A1", AS_OF.minusDays(6), SettlementAdjustmentType.REDUCE_OUTSTANDING, pkr("100.00")))
        );
        assertThrows(ObligationAgingException.class,
                () -> ObligationAgingCalculator.calculate(AS_OF, CURRENCY_PKR, List.of(adjBeforeIssue)));

        // Non-positive original amount
        ObligationHistory zeroAmount = createObligation("ZERO-AMT", AS_OF.minusDays(5), AS_OF, BigDecimal.ZERO);
        assertThrows(ObligationAgingException.class,
                () -> ObligationAgingCalculator.calculate(AS_OF, CURRENCY_PKR, List.of(zeroAmount)));

        // Non-positive adjustment amount
        ObligationHistory zeroAdj = new ObligationHistory(
                "ZERO-ADJ",
                ObligationDirection.RECEIVABLE,
                AS_OF.minusDays(5),
                AS_OF,
                pkr("1000.00"),
                null,
                List.of(new SettlementAdjustment("A1", AS_OF.minusDays(2), SettlementAdjustmentType.REDUCE_OUTSTANDING, BigDecimal.ZERO))
        );
        assertThrows(ObligationAgingException.class,
                () -> ObligationAgingCalculator.calculate(AS_OF, CURRENCY_PKR, List.of(zeroAdj)));
    }

    @Test
    @DisplayName("Exact decimals enforced: excess decimal places rejected without silent rounding")
    void testExactDecimalsEnforced() {
        // 3 decimal places in original amount
        ObligationHistory fractionalOriginal = createObligation(
                "FRAC-ORIG",
                AS_OF.minusDays(5),
                AS_OF,
                new BigDecimal("1000.555")
        );
        ObligationAgingException ex1 = assertThrows(ObligationAgingException.class,
                () -> ObligationAgingCalculator.calculate(AS_OF, CURRENCY_PKR, List.of(fractionalOriginal)));
        assertEquals("originalAmount", ex1.getField());

        // 3 decimal places in adjustment amount
        ObligationHistory fractionalAdj = new ObligationHistory(
                "FRAC-ADJ",
                ObligationDirection.RECEIVABLE,
                AS_OF.minusDays(5),
                AS_OF,
                pkr("1000.00"),
                null,
                List.of(new SettlementAdjustment("A1", AS_OF.minusDays(2), SettlementAdjustmentType.REDUCE_OUTSTANDING, new BigDecimal("100.005")))
        );
        ObligationAgingException ex2 = assertThrows(ObligationAgingException.class,
                () -> ObligationAgingCalculator.calculate(AS_OF, CURRENCY_PKR, List.of(fractionalAdj)));
        assertEquals("adjustmentAmount", ex2.getField());
    }

    @Test
    @DisplayName("Large aggregate amounts supported without artificial capacity capping")
    void testLargeAggregateAmounts() {
        // Multiple 50-billion PKR obligations
        BigDecimal largeAmount = new BigDecimal("50000000000.00");
        ObligationHistory r1 = createObligation("L-1", AS_OF.minusDays(5), AS_OF.minusDays(2), largeAmount);
        ObligationHistory r2 = createObligation("L-2", AS_OF.minusDays(5), AS_OF.minusDays(1), largeAmount);

        ObligationAgingResult result = ObligationAgingCalculator.calculate(AS_OF, CURRENCY_PKR, List.of(r1, r2));

        // 100 Billion PKR
        BigDecimal expectedTotal = new BigDecimal("100000000000.00");
        assertEquals(expectedTotal, result.receivableTotalOutstanding());
        assertEquals(expectedTotal, result.receivableOverdueOutstanding());
    }

    @Test
    @DisplayName("Immutability of result and safety against post-calculation list mutations")
    void testImmutabilityAndSafety() {
        List<ObligationHistory> inputList = new ArrayList<>();
        inputList.add(createObligation("IMM-1", AS_OF.minusDays(5), AS_OF, pkr("1000.00")));

        ObligationAgingResult result = ObligationAgingCalculator.calculate(AS_OF, CURRENCY_PKR, inputList);

        // Mutating input list after calculation has no effect
        inputList.add(createObligation("IMM-2", AS_OF.minusDays(5), AS_OF, pkr("2000.00")));
        assertEquals(1, result.details().size());

        // Result details list is unmodifiable
        assertThrows(UnsupportedOperationException.class,
                () -> result.details().add(null));

        // Result buckets map is unmodifiable
        assertThrows(UnsupportedOperationException.class,
                () -> result.receivables().buckets().put(AgingBucket.DUE_TODAY, null));
    }

    @Test
    @DisplayName("Deterministic detail ordering: RECEIVABLES then PAYABLES, then issueDate, then obligationId")
    void testDeterministicDetailOrdering() {
        ObligationHistory p2 = new ObligationHistory("P-2", ObligationDirection.PAYABLE, AS_OF.minusDays(5), null, pkr("100.00"), null, List.of());
        ObligationHistory p1 = new ObligationHistory("P-1", ObligationDirection.PAYABLE, AS_OF.minusDays(10), null, pkr("100.00"), null, List.of());
        ObligationHistory r2 = new ObligationHistory("R-B", ObligationDirection.RECEIVABLE, AS_OF.minusDays(5), null, pkr("100.00"), null, List.of());
        ObligationHistory r1 = new ObligationHistory("R-A", ObligationDirection.RECEIVABLE, AS_OF.minusDays(5), null, pkr("100.00"), null, List.of());
        ObligationHistory r0 = new ObligationHistory("R-OLD", ObligationDirection.RECEIVABLE, AS_OF.minusDays(20), null, pkr("100.00"), null, List.of());

        // Pass in jumbled order
        ObligationAgingResult result = ObligationAgingCalculator.calculate(AS_OF, CURRENCY_PKR, List.of(p2, r2, p1, r0, r1));

        List<String> order = result.details().stream().map(ObligationAgingDetail::obligationId).toList();
        // Receivables first: R-OLD (issued -20), then R-A (issued -5), then R-B (issued -5)
        // Then Payables: P-1 (issued -10), then P-2 (issued -5)
        assertEquals(List.of("R-OLD", "R-A", "R-B", "P-1", "P-2"), order);
    }

    @Test
    @DisplayName("Seven buckets completeness: all seven exact buckets present in every summary and reconcile with active details")
    void testSevenBucketsCompletenessAndReconciliation() {
        // Confirm enum defines exactly seven buckets
        assertEquals(7, AgingBucket.values().length);
        List<AgingBucket> expectedBuckets = List.of(
                AgingBucket.UNKNOWN_DUE_DATE,
                AgingBucket.NOT_YET_DUE,
                AgingBucket.DUE_TODAY,
                AgingBucket.OVERDUE_1_30,
                AgingBucket.OVERDUE_31_60,
                AgingBucket.OVERDUE_61_90,
                AgingBucket.OVERDUE_91_PLUS
        );
        assertEquals(expectedBuckets, List.of(AgingBucket.values()));

        // Empty calculation produces 7 zero-entries per direction
        ObligationAgingResult emptyResult = ObligationAgingCalculator.calculate(AS_OF, CURRENCY_PKR, List.of());
        for (DirectionAgingSummary summary : List.of(emptyResult.receivables(), emptyResult.payables())) {
            assertEquals(7, summary.buckets().size());
            for (AgingBucket bucket : AgingBucket.values()) {
                BucketSummary bs = summary.getBucket(bucket);
                assertNotNull(bs);
                assertEquals(bucket, bs.bucket());
                assertEquals(pkr("0.00"), bs.totalOutstanding());
                assertEquals(0, bs.count());
            }
            assertEquals(pkr("0.00"), summary.totalOutstanding());
            assertEquals(pkr("0.00"), summary.overdueOutstanding());
            assertEquals(0, summary.totalActiveCount());
            assertEquals(0, summary.overdueCount());
        }

        // Populated calculation reconciles bucket sums with direction totals
        ObligationHistory r1 = createObligation("REC-1", AS_OF.minusDays(5), AS_OF.minusDays(2), pkr("250.00"));
        ObligationHistory r2 = new ObligationHistory("REC-2", ObligationDirection.RECEIVABLE, AS_OF.minusDays(5), null, pkr("350.00"), null, List.of());
        ObligationHistory p1 = new ObligationHistory("PAY-1", ObligationDirection.PAYABLE, AS_OF.minusDays(5), AS_OF.plusDays(5), pkr("500.00"), null, List.of());

        ObligationAgingResult result = ObligationAgingCalculator.calculate(AS_OF, CURRENCY_PKR, List.of(r1, r2, p1));

        for (DirectionAgingSummary summary : List.of(result.receivables(), result.payables())) {
            assertEquals(7, summary.buckets().size());

            BigDecimal bucketSum = BigDecimal.ZERO.setScale(2);
            BigDecimal overdueSum = BigDecimal.ZERO.setScale(2);
            int countSum = 0;
            int overdueCountSum = 0;

            for (AgingBucket bucket : AgingBucket.values()) {
                BucketSummary bs = summary.getBucket(bucket);
                assertNotNull(bs);
                assertEquals(bucket, bs.bucket());
                bucketSum = bucketSum.add(bs.totalOutstanding());
                countSum += bs.count();

                if (bucket.isOverdue()) {
                    overdueSum = overdueSum.add(bs.totalOutstanding());
                    overdueCountSum += bs.count();
                }
            }

            assertEquals(summary.totalOutstanding(), bucketSum);
            assertEquals(summary.totalActiveCount(), countSum);
            assertEquals(summary.overdueOutstanding(), overdueSum);
            assertEquals(summary.overdueCount(), overdueCountSum);
        }
    }

    private ObligationHistory createObligation(String id, LocalDate issueDate, LocalDate dueDate, BigDecimal amount) {
        return new ObligationHistory(
                id,
                ObligationDirection.RECEIVABLE,
                issueDate,
                dueDate,
                amount,
                null,
                List.of()
        );
    }

    private ObligationAgingDetail findDetail(ObligationAgingResult result, String obligationId) {
        return result.details().stream()
                .filter(d -> obligationId.equals(d.obligationId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Detail not found for obligationId: " + obligationId));
    }
}
