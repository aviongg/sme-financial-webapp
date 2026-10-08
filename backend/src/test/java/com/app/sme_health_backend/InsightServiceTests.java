package com.app.sme_health_backend;

import com.app.sme_health_backend.i18n.TranslationService;
import com.app.sme_health_backend.insight.entity.Insight;
import com.app.sme_health_backend.insight.repository.InsightRepository;
import com.app.sme_health_backend.insight.service.InsightService;
import com.app.sme_health_backend.scoring.dto.ComponentScoresDto;
import com.app.sme_health_backend.scoring.entity.ScoreResult;
import com.app.sme_health_backend.shared.advice.AdviceContext;
import com.app.sme_health_backend.shared.advice.AdviceContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InsightServiceTests {

    @Mock
    private InsightRepository insightRepository;
    @Mock
    private AdviceContextService adviceContextService;

    private InsightService insightService;
    private UUID userId;
    private static final LocalDateTime COMPUTED_AT = LocalDateTime.of(2026, 9, 18, 12, 30);

    @BeforeEach
    void setUp() {
        insightService = new InsightService(insightRepository, adviceContextService,
                new TranslationService(new ObjectMapper()));
        userId = UUID.randomUUID();
    }

    @Test
    void shouldGenerateThreeInsightsFromCanonicalScoreIncludingInactiveComponents() {
        ScoreResult score = score("2026-09", "48.00", "Needs Attention", "0.65", "profitability");

        List<Insight> insights = insightService.generateInsights(score);

        assertEquals(3, insights.size());
        assertEquals(List.of("profitability", "overall_health", "data_quality"),
                insights.stream().map(Insight::getCategory).toList());
        assertEquals(List.of("high", "high", "high"),
                insights.stream().map(Insight::getPriority).toList());
        assertTrue(insights.stream().allMatch(i -> userId.equals(i.getUserId())
                && "2026-09".equals(i.getMonth()) && "en".equals(i.getLanguage())
                && COMPUTED_AT.equals(i.getSourceComputedAt()) && !i.getText().isBlank()));
        verifyNoInteractions(insightRepository, adviceContextService);
    }

    @ParameterizedTest
    @CsvSource({"Strong,low", "Stable,medium", "Needs Attention,high", "At Risk,high"})
    void shouldUseSuppliedBandForOverallPriority(String band, String priority) {
        ScoreResult score = score("2026-09", "75.00", band, "0.90", "cashflow");
        assertEquals(priority, insightService.generateInsights(score).get(1).getPriority());
    }

    @ParameterizedTest
    @CsvSource({"70.00,improved by 2 points,low", "74.00,declined by 2 points,high",
            "72.00,unchanged,low"})
    void shouldExplainExactPreviousMonthDelta(String previousValue, String text, String priority) {
        ScoreResult current = score("2026-09", "72.00", "Stable", "0.90", "cashflow");
        ScoreResult previous = score("2026-08", previousValue, "Stable", "0.90", "cashflow");

        List<Insight> insights = insightService.generateInsights(current, "en", previous);

        assertEquals(4, insights.size());
        Insight change = insights.get(3);
        assertEquals("monthly_change", change.getCategory());
        assertEquals(priority, change.getPriority());
        assertTrue(change.getText().contains(text));
        assertTrue(change.getText().contains("2026-08"));
    }

    @Test
    void shouldHandleYearBoundaryAndTranslateTheMonthlyDelta() {
        ScoreResult current = score("2026-01", "72.00", "Stable", "0.90", "cashflow");
        ScoreResult previous = score("2025-12", "70.00", "Stable", "0.90", "cashflow");

        List<Insight> insights = insightService.generateInsights(current, "ur", previous);

        assertEquals(4, insights.size());
        assertTrue(insights.get(0).getText().contains("کیش فلو"));
        assertTrue(insights.get(3).getText().contains("بہتر"));
        assertTrue(insights.get(3).getText().contains("2025-12"));
        assertTrue(insights.stream().allMatch(i -> "ur".equals(i.getLanguage())));
    }

    @ParameterizedTest
    @CsvSource({"70.00,بہتر", "74.00,کم", "72.00,برقرار"})
    void shouldTranslateEveryDeltaDirectionIntoUrdu(String previousValue, String expectedText) {
        ScoreResult current = score("2026-09", "72.00", "Stable", "0.90", "cashflow");
        ScoreResult previous = score("2026-08", previousValue, "Stable", "0.90", "cashflow");

        Insight delta = insightService.generateInsights(current, "ur", previous).get(3);

        assertEquals("ur", delta.getLanguage());
        assertTrue(delta.getText().contains(expectedText));
        assertFalse(delta.getText().contains("{"));
    }

    @Test
    void shouldReturnEmptyListWhenScoreIsAbsent() {
        when(adviceContextService.latest(userId)).thenReturn(Optional.empty());

        List<Insight> insights = insightService.getInsights(userId);

        assertTrue(insights.isEmpty());
        verify(adviceContextService).latest(userId);
        verifyNoInteractions(insightRepository);
    }

    @Test
    void shouldRefreshAndPersistInsightsWhenExistingAreMissingOrStale() {
        ScoreResult score = score("2026-09", "75.00", "Stable", "0.90", "cashflow");
        AdviceContext context = new AdviceContext(score, null, "en", "fp-123");

        when(adviceContextService.latest(userId)).thenReturn(Optional.of(context));
        when(insightRepository.findByUserIdAndMonthOrderByCreatedAtDesc(userId, "2026-09"))
                .thenReturn(List.of());
        when(insightRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<Insight> insights = insightService.getInsights(userId);

        assertEquals(3, insights.size());
        verify(insightRepository).deleteByUserIdAndMonth(userId, "2026-09");
        verify(insightRepository).flush();
        verify(insightRepository).saveAll(anyList());
    }

    @Test
    void shouldReturnExistingInsightsWhenFingerprintAndContentMatch() {
        ScoreResult score = score("2026-09", "75.00", "Stable", "0.90", "cashflow");
        AdviceContext context = new AdviceContext(score, null, "en", "fp-123");

        List<Insight> generated = insightService.generateInsights(score, "en", null);
        generated.forEach(i -> i.setSourceVersion("fp-123"));

        when(adviceContextService.latest(userId)).thenReturn(Optional.of(context));
        when(insightRepository.findByUserIdAndMonthOrderByCreatedAtDesc(userId, "2026-09"))
                .thenReturn(generated);

        List<Insight> insights = insightService.getInsights(userId);

        assertEquals(3, insights.size());
        verify(insightRepository, never()).deleteByUserIdAndMonth(any(), any());
        verify(insightRepository, never()).saveAll(anyList());
    }

    private ScoreResult score(String month, String composite, String band, String completeness, String weakest) {
        ScoreResult result = new ScoreResult();
        result.setId(UUID.randomUUID());
        result.setUserId(userId);
        result.setMonth(month);
        result.setCompositeScore(new BigDecimal(composite));
        result.setBand(band);
        result.setDataCompleteness(new BigDecimal(completeness));
        result.setWeakestComponent(weakest);
        result.setComputedAt(COMPUTED_AT);
        result.setComponentScores(new ComponentScoresDto(
                new BigDecimal("60.00"),
                new BigDecimal("40.00"),
                new BigDecimal("70.00"),
                new BigDecimal("50.00"),
                new BigDecimal("80.00")
        ));
        return result;
    }
}
