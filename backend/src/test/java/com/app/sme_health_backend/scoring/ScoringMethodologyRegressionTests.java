package com.app.sme_health_backend.scoring;

import com.app.sme_health_backend.profile.entity.BusinessProfile;
import com.app.sme_health_backend.profile.repository.BusinessProfileRepository;
import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.records.repository.MonthlyRecordRepository;
import com.app.sme_health_backend.scoring.calculator.*;
import com.app.sme_health_backend.scoring.entity.ScoreResult;
import com.app.sme_health_backend.scoring.repository.ScoreResultRepository;
import com.app.sme_health_backend.scoring.service.ScoringService;
import com.app.sme_health_backend.scoring.service.ScoringMethodology;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Fixed numerical expectations from the pre-refinement methodology, using actual calculators. */
class ScoringMethodologyRegressionTests {
    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "one_month,1,retail,1000,600,200,200,100,80,true,77.65,Stable,cashflow,0.85,50,100,80,,100",
            "two_months,2,retail,1000,600,200,200,100,80,true,77.65,Stable,cashflow,0.85,50,100,80,,100",
            "three_months,3,retail,1000,600,200,200,100,80,true,82.50,Strong,trend,1.00,80,100,80,50,100",
            "six_months,6,retail,1000,600,200,200,100,80,true,82.50,Strong,trend,1.00,80,100,80,50,100",
            "missing_optional,1,retail,1000,,200,100,100,,,59.09,Needs Attention,cashflow,0.55,25,100,,,",
            "missing_payment,3,retail,1000,600,200,200,100,,true,83.13,Strong,trend,0.80,80,100,,50,100",
            "missing_compliance,3,retail,1000,600,200,200,100,80,,80.56,Strong,trend,0.90,80,100,80,50,",
            "negative_net_movement,6,retail,1000,600,200,200,-20,80,true,82.50,Strong,trend,1.00,80,100,80,50,100",
            "strong_buffer,1,retail,1000,600,200,400,100,80,true,95.29,Strong,repayment,0.85,100,100,80,,100",
            "weak_buffer,1,retail,1000,600,200,40,100,80,true,63.53,Stable,cashflow,0.85,10,100,80,,100",
            "zero_revenue,1,retail,0,0,200,200,100,80,true,68.33,Stable,cashflow,0.60,50,,80,,100",
            "trade,1,trade,1000,700,200,200,100,80,true,67.14,Stable,cashflow,0.85,50,64.29,80,,100",
            "manufacturing,1,manufacturing,1000,700,200,200,100,80,true,60.49,Stable,profitability,0.85,50,41.67,80,,100",
            "services,1,services,1000,700,200,200,100,80,true,56.41,Needs Attention,profitability,0.85,50,27.78,80,,100",
            "retail,1,retail,1000,700,200,200,100,80,true,62.94,Stable,cashflow,0.85,50,50,80,,100"
    })
    void unchangedCanonicalNumbers(String name, int months, String type, String revenue, String cogs,
            String opex, String balance, String net, String payment, Boolean compliance, String composite,
            String band, String weakest, String completeness, String cash, String profit, String repay,
            String trend, String registration) {
        Fixture fixture = new Fixture(months, type, revenue, cogs, opex, balance, net, payment, compliance);
        ScoreResult result = fixture.calculate();
        assertDecimal(composite, result.getCompositeScore());
        assertEquals(band, result.getBand());
        assertEquals(weakest, result.getWeakestComponent());
        assertDecimal(completeness, result.getDataCompleteness());
        assertDecimal(cash, result.getComponentScores().getCashflow());
        assertDecimal(profit, result.getComponentScores().getProfitability());
        assertDecimal(repay, result.getComponentScores().getRepayment());
        assertDecimal(trend, result.getComponentScores().getTrend());
        assertDecimal(registration, result.getComponentScores().getCompliance());
        assertEquals(ScoringMethodology.VERSION, result.getMethodologyVersion());
        var evidence = result.getExplanation();
        assertEquals(months, evidence.historyMonthsAvailable());
        assertEquals("SELF_DECLARED", evidence.components().get("repayment").evidenceType());
        assertEquals("SELF_DECLARED", evidence.components().get("compliance").evidenceType());
        assertEquals("CALCULATED", evidence.components().get("cashflow").evidenceType());
        assertEquals(months < 3 ? "BUFFER_ONLY" : "VARIANCE_AND_BUFFER", evidence.components().get("cashflow").basis());
        assertEquals(months < 3 ? "INSUFFICIENT_HISTORY" : "NET_CASH_FLOW_TREND", evidence.components().get("trend").basis());
        BigDecimal effectiveTotal = BigDecimal.ZERO;
        for (var component : evidence.components().values()) {
            assertEquals(component.score() == null ? "UNAVAILABLE" : "AVAILABLE", component.status());
            if (component.score() == null) assertDecimal("0", component.effectiveWeight());
            effectiveTotal = effectiveTotal.add(component.effectiveWeight());
        }
        assertTrue(effectiveTotal.subtract(BigDecimal.ONE).abs().compareTo(new BigDecimal("0.00000003")) < 0);
    }

    @Test void receivablesContinueToContributeTheUnchangedDsoScore() {
        Fixture fixture = new Fixture(1, "retail", "1000", "600", "200", "200", "100", "80", true);
        fixture.history.getFirst().setReceivablesOutstanding(new BigDecimal("750"));
        ScoreResult result = fixture.calculate();
        assertDecimal("92.50", result.getComponentScores().getProfitability());
        assertDecimal("75.44", result.getCompositeScore());
        assertEquals("22.5", com.app.sme_health_backend.shared.advice.EvidenceAdvice.driver(result, "profitability", "dso"));
    }

    @Test void onlyExactPreviousCalendarMonthContributesMovement() {
        Fixture fixture = new Fixture(3, "retail", "1000", "600", "200", "200", "100", "80", true);
        ScoreResult previous = new ScoreResult();
        previous.setUserId(fixture.businessId); previous.setMonth("2025-12");
        previous.setCompositeScore(new BigDecimal("77.65"));
        previous.setComponentScores(new com.app.sme_health_backend.scoring.dto.ComponentScoresDto(
                new BigDecimal("50"), new BigDecimal("100"), new BigDecimal("80"), null, new BigDecimal("100")));
        when(fixture.scores.findByUserIdAndMonth(fixture.businessId, "2025-12")).thenReturn(Optional.of(previous));
        ScoreResult result = fixture.calculate();
        assertEquals("2025-12", result.getExplanation().previousMonth());
        assertDecimal("4.85", result.getExplanation().overallDelta());
        assertDecimal("30", result.getExplanation().components().get("cashflow").delta());
        assertTrue(result.getExplanation().majorChanges().stream().anyMatch(change -> change.component().equals("trend")
                && change.previousScore() == null && change.currentScore().compareTo(new BigDecimal("50")) == 0 && change.delta() == null));
        when(fixture.scores.findByUserIdAndMonth(fixture.businessId, "2025-12")).thenReturn(Optional.empty());
        assertNull(fixture.calculate().getExplanation().overallDelta());
    }

    @Test void changesRefreshNextMonthComparisonWithoutChangingItsNumericScore() {
        Fixture fixture = new Fixture(1, "retail", "1000", "600", "200", "200", "100", "80", true);
        ScoreResult next = fixture.calculate();
        next.setMonth("2026-02");
        when(fixture.scores.findByUserIdAndMonth(fixture.businessId, "2026-02")).thenReturn(Optional.of(next));
        fixture.history.getFirst().setCashBalanceEom(new BigDecimal("40"));
        fixture.calculate();
        assertDecimal("77.65", next.getCompositeScore());
        assertDecimal("14.12", next.getExplanation().overallDelta());
    }

    private static void assertDecimal(String expected, BigDecimal actual) {
        if (expected == null) assertNull(actual); else { assertNotNull(actual); assertEquals(0, new BigDecimal(expected).compareTo(actual), expected + " != " + actual); }
    }

    private static final class Fixture {
        final UUID businessId = UUID.randomUUID();
        final ScoreResultRepository scores = mock(ScoreResultRepository.class);
        final List<MonthlyRecord> history = new ArrayList<>();
        final ScoringService service;
        Fixture(int months, String type, String revenue, String cogs, String opex, String balance,
                String net, String payment, Boolean compliance) {
            var profiles = mock(BusinessProfileRepository.class);
            var records = mock(MonthlyRecordRepository.class);
            BusinessProfile profile = new BusinessProfile();
            profile.setBusinessType(type); profile.setPaymentBehavior(payment == null ? null : "2weeks");
            profile.setNtnRegistered(compliance); profile.setBusinessRegistered(compliance);
            for (int i = 0; i < months; i++) {
                MonthlyRecord record = new MonthlyRecord();
                record.setUserId(businessId); record.setMonth(YearMonth.of(2026, 1).minusMonths(i).toString());
                record.setRevenue(new BigDecimal(revenue)); record.setCogs(cogs == null ? null : new BigDecimal(cogs));
                record.setOperatingExpenses(new BigDecimal(opex)); record.setCashBalanceEom(new BigDecimal(balance));
                record.setCashInflow(new BigDecimal("500").add(new BigDecimal(net))); record.setCashOutflow(new BigDecimal("500"));
                history.add(record);
            }
            when(profiles.findById(businessId)).thenReturn(Optional.of(profile));
            when(records.findByUserIdAndMonth(businessId, "2026-01")).thenReturn(Optional.of(history.getFirst()));
            when(records.findByUserIdOrderByMonthDesc(businessId)).thenReturn(history);
            when(scores.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            service = new ScoringService(profiles, records, scores, new CashFlowStabilityCalculator(),
                    new ProfitabilityEfficiencyCalculator(), new RepaymentCalculator(), new TrendCalculator(), new ComplianceCalculator());
        }
        ScoreResult calculate() { return service.calculateAndSaveScore(businessId, "2026-01"); }
    }
}
