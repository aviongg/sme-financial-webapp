package com.app.sme_health_backend.scoring;

import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.scoring.calculator.CashFlowStabilityCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CashFlowStabilityCalculatorTests {

    private CashFlowStabilityCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new CashFlowStabilityCalculator();
    }

    @Test
    void shouldReturnNullWhenHistoryIsEmpty() {
        assertNull(calculator.calculate(null));
        assertNull(calculator.calculate(List.of()));
    }

    @Test
    void shouldCalculateOneMonthScoreUsingBufferOnly() {
        MonthlyRecord record = createRecord("2026-09", "500000", "300000", "200000", "100000");
        List<MonthlyRecord> history = List.of(record);

        // bufferRatio = 200000 / 100000 = 2.0 -> bufferScore = 100.00
        BigDecimal score = calculator.calculate(history);
        assertNotNull(score);
        assertEquals(new BigDecimal("100.00"), score);
    }

    @Test
    void shouldCalculateTwoMonthsScoreUsingBufferOnly() {
        MonthlyRecord r1 = createRecord("2026-09", "500000", "400000", "100000", "100000");
        MonthlyRecord r2 = createRecord("2026-08", "500000", "400000", "100000", "100000");
        List<MonthlyRecord> history = List.of(r1, r2);

        // avgOpex = 100000, cashBalance = 100000 -> ratio = 1.0 -> bufferScore = 50.00
        BigDecimal score = calculator.calculate(history);
        assertNotNull(score);
        assertEquals(new BigDecimal("50.00"), score);
    }

    @Test
    void shouldReturnNullWhenOpexIsZeroAndBufferCannotBeCalculated() {
        MonthlyRecord record = createRecord("2026-09", "500000", "300000", "200000", "0");
        List<MonthlyRecord> history = List.of(record);

        // Zero opex -> bufferScore is null -> CashFlowStabilityScore is null (not fabricated 100)
        BigDecimal score = calculator.calculate(history);
        assertNull(score);
    }

    @Test
    void shouldCalculateScoreForThreeMonthsWithVarianceAndBuffer() {
        // 3 months of identical NCF: mean = 100,000, stdDev = 0 -> CV = 0 -> varianceScore = 100.00
        MonthlyRecord r1 = createRecord("2026-09", "300000", "200000", "200000", "100000");
        MonthlyRecord r2 = createRecord("2026-08", "300000", "200000", "200000", "100000");
        MonthlyRecord r3 = createRecord("2026-07", "300000", "200000", "200000", "100000");
        List<MonthlyRecord> history = List.of(r1, r2, r3);

        // varianceScore = 100.00, bufferScore = 100.00 -> combined = 0.6 * 100 + 0.4 * 100 = 100.00
        BigDecimal score = calculator.calculate(history);
        assertNotNull(score);
        assertEquals(new BigDecimal("100.00"), score);
    }

    @Test
    void shouldCapHistoryAtSixMonths() {
        List<MonthlyRecord> history = new ArrayList<>();
        for (int i = 8; i >= 1; i--) {
            history.add(createRecord("2026-0" + i, "300000", "200000", "100000", "100000"));
        }
        assertEquals(8, history.size());

        BigDecimal score = calculator.calculate(history);
        assertNotNull(score);
        // bufferRatio = 1.0 -> bufferScore = 50.00. Identical NCF -> varianceScore = 100.00
        // Combined: 0.6 * 100 + 0.4 * 50 = 60 + 20 = 80.00
        assertEquals(new BigDecimal("80.00"), score);
    }

    @Test
    void shouldHandleZeroMeanNetCashFlow() {
        // NCFs: +10000, -10000, 0 -> mean = 0 -> CV = 1.0 -> varianceScore = 0.00
        MonthlyRecord r1 = createRecord("2026-09", "10000", "0", "100000", "100000");
        MonthlyRecord r2 = createRecord("2026-08", "0", "10000", "100000", "100000");
        MonthlyRecord r3 = createRecord("2026-07", "0", "0", "100000", "100000");
        List<MonthlyRecord> history = List.of(r1, r2, r3);

        BigDecimal varianceScore = calculator.calculateVarianceScore(history);
        assertNotNull(varianceScore);
        assertEquals(new BigDecimal("0.00"), varianceScore);

        // Buffer: cash 100000, opex 100000 -> ratio 1.0 -> bufferScore = 50.00
        // Combined: 0.6 * 0 + 0.4 * 50 = 20.00
        BigDecimal score = calculator.calculate(history);
        assertNotNull(score);
        assertEquals(new BigDecimal("20.00"), score);
    }

    @Test
    void shouldHandleBufferBoundaryValues() {
        // ratio < 1 (0.5 -> 25.00)
        MonthlyRecord rLow = createRecord("2026-09", "100000", "50000", "50000", "100000");
        assertEquals(new BigDecimal("25.00"), calculator.calculateBufferScore(List.of(rLow)));

        // ratio = 1.0 -> 50.00
        MonthlyRecord rMid = createRecord("2026-09", "100000", "50000", "100000", "100000");
        assertEquals(new BigDecimal("50.00"), calculator.calculateBufferScore(List.of(rMid)));

        // ratio = 1.5 -> 50 + 0.5 * 50 = 75.00
        MonthlyRecord rMidHigh = createRecord("2026-09", "100000", "50000", "150000", "100000");
        assertEquals(new BigDecimal("75.00"), calculator.calculateBufferScore(List.of(rMidHigh)));

        // ratio >= 2.0 -> 100.00
        MonthlyRecord rHigh = createRecord("2026-09", "100000", "50000", "200000", "100000");
        assertEquals(new BigDecimal("100.00"), calculator.calculateBufferScore(List.of(rHigh)));
    }

    private MonthlyRecord createRecord(String month, String inflow, String outflow, String cashEom, String opex) {
        MonthlyRecord record = new MonthlyRecord();
        record.setMonth(month);
        record.setCashInflow(new BigDecimal(inflow));
        record.setCashOutflow(new BigDecimal(outflow));
        record.setCashBalanceEom(new BigDecimal(cashEom));
        record.setOperatingExpenses(new BigDecimal(opex));
        return record;
    }
}
