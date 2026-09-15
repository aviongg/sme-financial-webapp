package com.app.sme_health_backend.scoring;

import com.app.sme_health_backend.profile.entity.BusinessProfile;
import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.scoring.calculator.ProfitabilityEfficiencyCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ProfitabilityEfficiencyCalculatorTests {

    private ProfitabilityEfficiencyCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new ProfitabilityEfficiencyCalculator();
    }

    @Test
    void shouldReturnNullWhenRecordIsNull() {
        assertNull(calculator.calculate(null, new BusinessProfile()));
    }

    @Test
    void shouldReturnNullWhenRevenueIsZeroOrNegative() {
        MonthlyRecord record = new MonthlyRecord();
        record.setRevenue(BigDecimal.ZERO);
        record.setCogs(BigDecimal.ZERO);
        record.setOperatingExpenses(BigDecimal.ZERO);

        assertNull(calculator.calculate(record, new BusinessProfile()));
    }

    @Test
    void shouldScoreTradeBusinessTypeMargins() {
        BusinessProfile profile = new BusinessProfile();
        profile.setBusinessType("trade"); // good = 15%, ok = 8%

        // 15% net margin -> 100
        MonthlyRecord r1 = createRecord("1000000", "800000", "50000", null);
        assertEquals(new BigDecimal("100.00"), calculator.calculateNetMarginScore(r1, profile));

        // 8% net margin -> 50
        MonthlyRecord r2 = createRecord("1000000", "820000", "100000", null);
        assertEquals(new BigDecimal("50.00"), calculator.calculateNetMarginScore(r2, profile));

        // 4% net margin -> (0.04 / 0.08) * 50 = 25
        MonthlyRecord r3 = createRecord("1000000", "860000", "100000", null);
        assertEquals(new BigDecimal("25.00"), calculator.calculateNetMarginScore(r3, profile));
    }

    @Test
    void shouldScoreRetailBusinessTypeMargins() {
        BusinessProfile profile = new BusinessProfile();
        profile.setBusinessType("retail"); // good = 20%, ok = 10%

        // 20% margin -> 100
        MonthlyRecord r1 = createRecord("1000000", "700000", "100000", null);
        assertEquals(new BigDecimal("100.00"), calculator.calculateNetMarginScore(r1, profile));

        // 10% margin -> 50
        MonthlyRecord r2 = createRecord("1000000", "750000", "150000", null);
        assertEquals(new BigDecimal("50.00"), calculator.calculateNetMarginScore(r2, profile));
    }

    @Test
    void shouldScoreManufacturingBusinessTypeMargins() {
        BusinessProfile profile = new BusinessProfile();
        profile.setBusinessType("manufacturing"); // good = 25%, ok = 12%

        // 25% margin -> 100
        MonthlyRecord r1 = createRecord("1000000", "600000", "150000", null);
        assertEquals(new BigDecimal("100.00"), calculator.calculateNetMarginScore(r1, profile));

        // 12% margin -> 50
        MonthlyRecord r2 = createRecord("1000000", "700000", "180000", null);
        assertEquals(new BigDecimal("50.00"), calculator.calculateNetMarginScore(r2, profile));
    }

    @Test
    void shouldScoreServicesBusinessTypeMargins() {
        BusinessProfile profile = new BusinessProfile();
        profile.setBusinessType("services"); // good = 35%, ok = 18%

        // 35% margin -> 100
        MonthlyRecord r1 = createRecord("1000000", "0", "650000", null);
        assertEquals(new BigDecimal("100.00"), calculator.calculateNetMarginScore(r1, profile));

        // 18% margin -> 50
        MonthlyRecord r2 = createRecord("1000000", "0", "820000", null);
        assertEquals(new BigDecimal("50.00"), calculator.calculateNetMarginScore(r2, profile));
    }

    @Test
    void shouldScoreZeroForNegativeMargin() {
        BusinessProfile profile = new BusinessProfile();
        profile.setBusinessType("retail");

        // expenses exceed revenue -> negative net profit
        MonthlyRecord r = createRecord("500000", "400000", "200000", null);
        assertEquals(new BigDecimal("0.00"), calculator.calculateNetMarginScore(r, profile));
    }

    @Test
    void shouldCalculateDsoScoresCorrectly() {
        // DSO <= 15 -> 100 (e.g. receivables = 50,000, revenue = 1,000,000 -> DSO = 1.5)
        MonthlyRecord r15 = createRecord("1000000", "500000", "200000", "500000"); // DSO = 15
        assertEquals(new BigDecimal("100.00"), calculator.calculateDsoScore(r15));

        // DSO = 30 -> 70 (receivables = 1,000,000, revenue = 1,000,000 -> DSO = 30)
        MonthlyRecord r30 = createRecord("1000000", "500000", "200000", "1000000");
        assertEquals(new BigDecimal("70.00"), calculator.calculateDsoScore(r30));

        // DSO = 60 -> 30 (receivables = 2,000,000, revenue = 1,000,000 -> DSO = 60)
        MonthlyRecord r60 = createRecord("1000000", "500000", "200000", "2000000");
        assertEquals(new BigDecimal("30.00"), calculator.calculateDsoScore(r60));

        // DSO = 120 -> 0 (receivables = 4,000,000, revenue = 1,000,000 -> DSO = 120 -> 30 - 60*0.5 = 0)
        MonthlyRecord r120 = createRecord("1000000", "500000", "200000", "4000000");
        assertEquals(new BigDecimal("0.00"), calculator.calculateDsoScore(r120));

        // DSO > 120 -> 0
        MonthlyRecord r150 = createRecord("1000000", "500000", "200000", "5000000");
        assertEquals(new BigDecimal("0.00"), calculator.calculateDsoScore(r150));
    }

    @Test
    void shouldScoreUsingNetMarginOnlyWhenDsoIsUnavailable() {
        BusinessProfile profile = new BusinessProfile();
        profile.setBusinessType("retail");

        // Net margin = 20% -> 100. DSO is null.
        MonthlyRecord r = createRecord("1000000", "700000", "100000", null);
        BigDecimal score = calculator.calculate(r, profile);
        assertNotNull(score);
        assertEquals(new BigDecimal("100.00"), score);
    }

    @Test
    void shouldAverageNetMarginAndDsoWhenBothAvailable() {
        BusinessProfile profile = new BusinessProfile();
        profile.setBusinessType("retail");

        // Net margin = 20% -> 100. DSO = 30 -> 70.
        // Average: (100 + 70) / 2 = 85.00
        MonthlyRecord r = createRecord("1000000", "700000", "100000", "1000000");
        BigDecimal score = calculator.calculate(r, profile);
        assertNotNull(score);
        assertEquals(new BigDecimal("85.00"), score);
    }

    private MonthlyRecord createRecord(String rev, String cogs, String opex, String receivables) {
        MonthlyRecord record = new MonthlyRecord();
        record.setRevenue(new BigDecimal(rev));
        record.setCogs(cogs != null ? new BigDecimal(cogs) : BigDecimal.ZERO);
        record.setOperatingExpenses(opex != null ? new BigDecimal(opex) : BigDecimal.ZERO);
        if (receivables != null) {
            record.setReceivablesOutstanding(new BigDecimal(receivables));
        }
        return record;
    }
}
