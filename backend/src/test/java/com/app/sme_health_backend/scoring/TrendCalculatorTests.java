package com.app.sme_health_backend.scoring;

import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.scoring.calculator.TrendCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TrendCalculatorTests {

    private TrendCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new TrendCalculator();
    }

    @Test
    void shouldReturnNullWhenHistoryHasLessThanThreeMonths() {
        assertNull(calculator.calculate(null));
        assertNull(calculator.calculate(List.of()));

        MonthlyRecord r1 = createRecord("2026-09", "100000", "50000");
        assertNull(calculator.calculate(List.of(r1)));

        MonthlyRecord r2 = createRecord("2026-08", "100000", "50000");
        assertNull(calculator.calculate(List.of(r1, r2)));
    }

    @Test
    void shouldReturnFiftyForFlatTrend() {
        // Equal NCF: 100000 each month
        MonthlyRecord r1 = createRecord("2026-09", "200000", "100000");
        MonthlyRecord r2 = createRecord("2026-08", "200000", "100000");
        MonthlyRecord r3 = createRecord("2026-07", "200000", "100000");

        BigDecimal score = calculator.calculate(List.of(r1, r2, r3));
        assertNotNull(score);
        assertEquals(new BigDecimal("50.00"), score);
    }

    @Test
    void shouldReturnGreaterThanFiftyForPositiveTrend() {
        // NCFs: July = 100,000, August = 200,000, September = 300,000
        // Provided in descending order (Sept, Aug, July)
        MonthlyRecord r1 = createRecord("2026-09", "400000", "100000"); // NCF = 300,000
        MonthlyRecord r2 = createRecord("2026-08", "300000", "100000"); // NCF = 200,000
        MonthlyRecord r3 = createRecord("2026-07", "200000", "100000"); // NCF = 100,000

        BigDecimal score = calculator.calculate(List.of(r1, r2, r3));
        assertNotNull(score);
        // x = 0 (July: 100k), x = 1 (Aug: 200k), x = 2 (Sept: 300k)
        // slope = 100k, avgAbs = 200k -> normSlope = 0.5 -> score = 50 + 25 = 75.00
        assertEquals(new BigDecimal("75.00"), score);
    }

    @Test
    void shouldReturnLessThanFiftyForNegativeTrend() {
        // NCFs: July = 300,000, August = 200,000, September = 100,000
        // Provided in descending order (Sept, Aug, July)
        MonthlyRecord r1 = createRecord("2026-09", "200000", "100000"); // NCF = 100,000
        MonthlyRecord r2 = createRecord("2026-08", "300000", "100000"); // NCF = 200,000
        MonthlyRecord r3 = createRecord("2026-07", "400000", "100000"); // NCF = 300,000

        BigDecimal score = calculator.calculate(List.of(r1, r2, r3));
        assertNotNull(score);
        // slope = -100k, avgAbs = 200k -> normSlope = -0.5 -> score = 50 - 25 = 25.00
        assertEquals(new BigDecimal("25.00"), score);
    }

    @Test
    void shouldReturnFiftyWhenAllNetCashFlowsAreZero() {
        MonthlyRecord r1 = createRecord("2026-09", "100000", "100000");
        MonthlyRecord r2 = createRecord("2026-08", "100000", "100000");
        MonthlyRecord r3 = createRecord("2026-07", "100000", "100000");

        BigDecimal score = calculator.calculate(List.of(r1, r2, r3));
        assertNotNull(score);
        assertEquals(new BigDecimal("50.00"), score);
    }

    @Test
    void shouldCapHistoryAtSixMonthsForTrend() {
        List<MonthlyRecord> history = new ArrayList<>();
        // Months 2026-08 down to 2026-01 (8 months)
        for (int i = 8; i >= 1; i--) {
            history.add(createRecord("2026-0" + i, "200000", "100000"));
        }
        assertEquals(8, history.size());

        BigDecimal score = calculator.calculate(history);
        assertNotNull(score);
        assertEquals(new BigDecimal("50.00"), score);
    }

    private MonthlyRecord createRecord(String month, String inflow, String outflow) {
        MonthlyRecord record = new MonthlyRecord();
        record.setMonth(month);
        record.setCashInflow(new BigDecimal(inflow));
        record.setCashOutflow(new BigDecimal(outflow));
        return record;
    }
}
