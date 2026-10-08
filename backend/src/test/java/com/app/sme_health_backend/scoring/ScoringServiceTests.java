package com.app.sme_health_backend.scoring;

import com.app.sme_health_backend.profile.entity.BusinessProfile;
import com.app.sme_health_backend.profile.repository.BusinessProfileRepository;
import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.records.repository.MonthlyRecordRepository;
import com.app.sme_health_backend.scoring.calculator.*;
import com.app.sme_health_backend.scoring.entity.ScoreResult;
import com.app.sme_health_backend.scoring.repository.ScoreResultRepository;
import com.app.sme_health_backend.scoring.service.ScoringService;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScoringServiceTests {

    @Mock
    private BusinessProfileRepository businessProfileRepository;

    @Mock
    private MonthlyRecordRepository monthlyRecordRepository;

    @Mock
    private ScoreResultRepository scoreResultRepository;

    @Mock
    private CashFlowStabilityCalculator cashFlowStabilityCalculator;

    @Mock
    private ProfitabilityEfficiencyCalculator profitabilityEfficiencyCalculator;

    @Mock
    private RepaymentCalculator repaymentCalculator;

    @Mock
    private TrendCalculator trendCalculator;

    @Mock
    private ComplianceCalculator complianceCalculator;

    private ScoringService scoringService;
    private UUID userId;

    @BeforeEach
    void setUp() {
        scoringService = new ScoringService(
                businessProfileRepository,
                monthlyRecordRepository,
                scoreResultRepository,
                cashFlowStabilityCalculator,
                profitabilityEfficiencyCalculator,
                repaymentCalculator,
                trendCalculator,
                complianceCalculator
        );
        userId = UUID.randomUUID();
    }

    @Test
    void shouldRejectInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> scoringService.calculateAndSaveScore(null, "2026-09"));
        assertThrows(IllegalArgumentException.class, () -> scoringService.calculateAndSaveScore(userId, null));
        assertThrows(IllegalArgumentException.class, () -> scoringService.calculateAndSaveScore(userId, "invalid-month"));
    }

    @Test
    void shouldThrowWhenBusinessProfileNotFound() {
        when(businessProfileRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                scoringService.calculateAndSaveScore(userId, "2026-09"));
    }

    @Test
    void shouldThrowWhenMonthlyRecordNotFound() {
        BusinessProfile profile = new BusinessProfile();
        when(businessProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(monthlyRecordRepository.findByUserIdAndMonth(userId, "2026-09")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                scoringService.calculateAndSaveScore(userId, "2026-09"));
    }

    @Test
    void shouldCalculateScoreWithAllFiveComponents() {
        BusinessProfile profile = new BusinessProfile();
        profile.setUserId(userId);

        MonthlyRecord record = new MonthlyRecord();
        record.setUserId(userId);
        record.setMonth("2026-09");

        when(businessProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(monthlyRecordRepository.findByUserIdAndMonth(userId, "2026-09")).thenReturn(Optional.of(record));
        when(monthlyRecordRepository.findByUserIdOrderByMonthDesc(userId)).thenReturn(List.of(record));

        // 5 scores:
        // Cashflow = 80 (wt 0.30) -> 24
        // Profitability = 70 (wt 0.25) -> 17.5
        // Repayment = 60 (wt 0.20) -> 12
        // Trend = 90 (wt 0.15) -> 13.5
        // Compliance = 100 (wt 0.10) -> 10
        // Weighted sum = 24 + 17.5 + 12 + 13.5 + 10 = 77.00
        // availableWeight = 1.00 -> composite = 77.00 (Stable)
        when(cashFlowStabilityCalculator.calculate(any())).thenReturn(new BigDecimal("80.00"));
        when(profitabilityEfficiencyCalculator.calculate(any(), any())).thenReturn(new BigDecimal("70.00"));
        when(repaymentCalculator.calculate(any())).thenReturn(new BigDecimal("60.00"));
        when(trendCalculator.calculate(any())).thenReturn(new BigDecimal("90.00"));
        when(complianceCalculator.calculate(any())).thenReturn(new BigDecimal("100.00"));

        when(scoreResultRepository.findByUserIdAndMonth(userId, "2026-09")).thenReturn(Optional.empty());
        when(scoreResultRepository.save(any(ScoreResult.class))).thenAnswer(inv -> inv.getArgument(0));

        ScoreResult result = scoringService.calculateAndSaveScore(userId, "2026-09");

        assertNotNull(result);
        assertEquals(new BigDecimal("77.00"), result.getCompositeScore());
        assertEquals("Stable", result.getBand());
        assertEquals("repayment", result.getWeakestComponent());
        assertEquals(new BigDecimal("1.00"), result.getDataCompleteness());
    }

    @Test
    void shouldDynamicallyRedistributeWeightWhenComponentsAreMissing() {
        BusinessProfile profile = new BusinessProfile();
        profile.setUserId(userId);

        MonthlyRecord record = new MonthlyRecord();
        record.setUserId(userId);
        record.setMonth("2026-09");

        when(businessProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(monthlyRecordRepository.findByUserIdAndMonth(userId, "2026-09")).thenReturn(Optional.of(record));
        when(monthlyRecordRepository.findByUserIdOrderByMonthDesc(userId)).thenReturn(List.of(record));

        // Cashflow = 80.00 (wt 0.30)
        // Profitability = 60.00 (wt 0.25)
        // Repayment = null
        // Trend = null
        // Compliance = null
        // availableWeight = 0.55
        // weightedTotal = (80 * 0.30) + (60 * 0.25) = 24 + 15 = 39.00
        // composite = 39.00 / 0.55 = 70.91 (Stable)
        when(cashFlowStabilityCalculator.calculate(any())).thenReturn(new BigDecimal("80.00"));
        when(profitabilityEfficiencyCalculator.calculate(any(), any())).thenReturn(new BigDecimal("60.00"));
        when(repaymentCalculator.calculate(any())).thenReturn(null);
        when(trendCalculator.calculate(any())).thenReturn(null);
        when(complianceCalculator.calculate(any())).thenReturn(null);

        when(scoreResultRepository.findByUserIdAndMonth(userId, "2026-09")).thenReturn(Optional.empty());
        when(scoreResultRepository.save(any(ScoreResult.class))).thenAnswer(inv -> inv.getArgument(0));

        ScoreResult result = scoringService.calculateAndSaveScore(userId, "2026-09");

        assertNotNull(result);
        assertEquals(new BigDecimal("70.91"), result.getCompositeScore());
        assertEquals("Stable", result.getBand());
        assertEquals("profitability", result.getWeakestComponent());
        assertEquals(new BigDecimal("0.55"), result.getDataCompleteness());
    }

    @Test
    void shouldThrowExceptionWhenAllComponentsAreUnavailable() {
        BusinessProfile profile = new BusinessProfile();
        profile.setUserId(userId);

        MonthlyRecord record = new MonthlyRecord();
        record.setUserId(userId);
        record.setMonth("2026-09");

        when(businessProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(monthlyRecordRepository.findByUserIdAndMonth(userId, "2026-09")).thenReturn(Optional.of(record));
        when(monthlyRecordRepository.findByUserIdOrderByMonthDesc(userId)).thenReturn(List.of(record));

        when(cashFlowStabilityCalculator.calculate(any())).thenReturn(null);
        when(profitabilityEfficiencyCalculator.calculate(any(), any())).thenReturn(null);
        when(repaymentCalculator.calculate(any())).thenReturn(null);
        when(trendCalculator.calculate(any())).thenReturn(null);
        when(complianceCalculator.calculate(any())).thenReturn(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                scoringService.calculateAndSaveScore(userId, "2026-09"));

        assertEquals("Insufficient financial data to calculate financial health score", ex.getMessage());
        verify(scoreResultRepository, never()).save(any());
    }

    @Test
    void shouldBreakTiesForWeakestComponentDeterministically() {
        // Priority: cashflow > profitability > repayment > trend > compliance
        // If cashflow and profitability both have lowest score of 50.00 -> cashflow wins
        Map<String, BigDecimal> scores1 = Map.of(
                "cashflow", new BigDecimal("50.00"),
                "profitability", new BigDecimal("50.00"),
                "repayment", new BigDecimal("80.00")
        );
        assertEquals("cashflow", scoringService.determineWeakestComponent(scores1));

        // If profitability and repayment both have lowest score of 40.00 -> profitability wins
        Map<String, BigDecimal> scores2 = Map.of(
                "cashflow", new BigDecimal("80.00"),
                "profitability", new BigDecimal("40.00"),
                "repayment", new BigDecimal("40.00")
        );
        assertEquals("profitability", scoringService.determineWeakestComponent(scores2));

        // If trend and compliance both have lowest score of 30.00 -> trend wins
        Map<String, BigDecimal> scores3 = Map.of(
                "cashflow", new BigDecimal("80.00"),
                "profitability", new BigDecimal("60.00"),
                "trend", new BigDecimal("30.00"),
                "compliance", new BigDecimal("30.00")
        );
        assertEquals("trend", scoringService.determineWeakestComponent(scores3));
    }

    @Test
    void shouldDetermineBandBoundariesAccurately() {
        assertEquals("Strong", scoringService.determineBand(new BigDecimal("100.00")));
        assertEquals("Strong", scoringService.determineBand(new BigDecimal("80.00")));
        assertEquals("Stable", scoringService.determineBand(new BigDecimal("79.99")));
        assertEquals("Stable", scoringService.determineBand(new BigDecimal("60.00")));
        assertEquals("Needs Attention", scoringService.determineBand(new BigDecimal("59.99")));
        assertEquals("Needs Attention", scoringService.determineBand(new BigDecimal("40.00")));
        assertEquals("At Risk", scoringService.determineBand(new BigDecimal("39.99")));
        assertEquals("At Risk", scoringService.determineBand(new BigDecimal("0.00")));
        assertEquals("At Risk", scoringService.determineBand(null));
    }

    @Test
    void shouldUpdateExistingScoreResultOnRecalculate() {
        BusinessProfile profile = new BusinessProfile();
        profile.setUserId(userId);

        MonthlyRecord record = new MonthlyRecord();
        record.setUserId(userId);
        record.setMonth("2026-09");

        ScoreResult existing = new ScoreResult();
        existing.setUserId(userId);
        existing.setMonth("2026-09");
        existing.setCompositeScore(new BigDecimal("50.00"));

        when(businessProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(monthlyRecordRepository.findByUserIdAndMonth(userId, "2026-09")).thenReturn(Optional.of(record));
        when(monthlyRecordRepository.findByUserIdOrderByMonthDesc(userId)).thenReturn(List.of(record));

        when(cashFlowStabilityCalculator.calculate(any())).thenReturn(new BigDecimal("90.00"));
        when(profitabilityEfficiencyCalculator.calculate(any(), any())).thenReturn(null);
        when(repaymentCalculator.calculate(any())).thenReturn(null);
        when(trendCalculator.calculate(any())).thenReturn(null);
        when(complianceCalculator.calculate(any())).thenReturn(null);

        when(scoreResultRepository.findByUserIdAndMonth(userId, "2026-09")).thenReturn(Optional.of(existing));
        when(scoreResultRepository.save(existing)).thenReturn(existing);

        ScoreResult result = scoringService.calculateAndSaveScore(userId, "2026-09");

        assertNotNull(result);
        assertEquals(new BigDecimal("90.00"), existing.getCompositeScore());
        assertEquals("Strong", existing.getBand());
        verify(scoreResultRepository, times(1)).save(existing);
    }
}
