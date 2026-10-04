package com.app.sme_health_backend;

import com.app.sme_health_backend.i18n.TranslationService;
import com.app.sme_health_backend.recommendation.entity.Recommendation;
import com.app.sme_health_backend.recommendation.repository.RecommendationRepository;
import com.app.sme_health_backend.recommendation.service.RecommendationService;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTests {

    @Mock
    private RecommendationRepository recommendationRepository;

    @Mock
    private AdviceContextService adviceContextService;

    private RecommendationService recommendationService;
    private UUID userId;
    private static final LocalDateTime COMPUTED_AT = LocalDateTime.of(2026, 9, 15, 12, 30);

    @BeforeEach
    void setUp() {
        recommendationService = new RecommendationService(
                recommendationRepository, adviceContextService,
                new TranslationService(new ObjectMapper()));
        userId = UUID.randomUUID();
    }

    @Test
    void shouldGenerateComponentBandAndDataQualityAdviceFromContract() {
        ScoreResult score = scoreResult("Needs Attention", "profitability", "0.65");
        List<Recommendation> recommendations = recommendationService.generateRecommendations(score);

        assertEquals(3, recommendations.size());
        assertEquals(List.of("profitability", "overall_health", "data_quality"),
                recommendations.stream().map(Recommendation::getCategory).toList());
        assertEquals(List.of("high", "high", "high"),
                recommendations.stream().map(Recommendation::getPriority).toList());
        assertTrue(recommendations.stream().allMatch(r -> userId.equals(r.getUserId())
                && "2026-09".equals(r.getMonth()) && "en".equals(r.getLanguage())
                && COMPUTED_AT.equals(r.getSourceComputedAt()) && !r.getText().isBlank()));
        verifyNoInteractions(recommendationRepository, adviceContextService);
    }

    @ParameterizedTest
    @CsvSource({"Strong,low", "Stable,medium", "Needs Attention,high", "At Risk,high"})
    void shouldUseBandForOverallHealthPriority(String band, String priority) {
        ScoreResult score = scoreResult(band, "cashflow", "0.90");
        Recommendation overall = recommendationService.generateRecommendations(score).get(1);
        assertEquals("overall_health", overall.getCategory());
        assertEquals(priority, overall.getPriority());
    }

    @Test
    void shouldTranslateRecommendationsToUrdu() {
        ScoreResult score = scoreResult("Stable", "cashflow", "0.90");
        List<Recommendation> recommendations = recommendationService.generateRecommendations(score, "ur");

        assertEquals(3, recommendations.size());
        assertTrue(recommendations.get(0).getText().contains("کیش فلو"));
        assertTrue(recommendations.stream().allMatch(r -> "ur".equals(r.getLanguage())));
    }

    @Test
    void shouldReturnEmptyListWhenScoreIsAbsent() {
        when(adviceContextService.latest(userId)).thenReturn(Optional.empty());

        List<Recommendation> recommendations = recommendationService.getRecommendations(userId);

        assertTrue(recommendations.isEmpty());
        verify(adviceContextService).latest(userId);
        verifyNoInteractions(recommendationRepository);
    }

    @Test
    void shouldRefreshAndPersistRecommendationsWhenExistingAreMissingOrStale() {
        ScoreResult score = scoreResult("Stable", "cashflow", "0.90");
        AdviceContext context = new AdviceContext(score, null, "en", "fp-456");

        when(adviceContextService.latest(userId)).thenReturn(Optional.of(context));
        when(recommendationRepository.findByUserIdAndMonthOrderByCreatedAtDesc(userId, "2026-09"))
                .thenReturn(List.of());
        when(recommendationRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<Recommendation> recommendations = recommendationService.getRecommendations(userId);

        assertEquals(3, recommendations.size());
        verify(recommendationRepository, never()).deleteByUserIdAndMonth(any(), any());
        verify(recommendationRepository, never()).flush();
        verify(recommendationRepository).saveAll(anyList());
    }

    @Test
    void shouldReturnExistingRecommendationsWhenFingerprintAndContentMatch() {
        ScoreResult score = scoreResult("Stable", "cashflow", "0.90");
        AdviceContext context = new AdviceContext(score, null, "en", "fp-456");

        List<Recommendation> generated = recommendationService.generateRecommendations(score, "en");
        generated.forEach(r -> r.setSourceVersion("fp-456"));

        when(adviceContextService.latest(userId)).thenReturn(Optional.of(context));
        when(recommendationRepository.findByUserIdAndMonthOrderByCreatedAtDesc(userId, "2026-09"))
                .thenReturn(generated);

        List<Recommendation> recommendations = recommendationService.getRecommendations(userId);

        assertEquals(3, recommendations.size());
        verify(recommendationRepository, never()).deleteByUserIdAndMonth(any(), any());
        verify(recommendationRepository, never()).saveAll(anyList());
    }

    @Test
    void languageOnlyRegenerationPreservesIdentityDoneStateAndTimestamp() {
        ScoreResult score = scoreResult("Stable", "cashflow", "0.90");
        List<Recommendation> original = recommendationService.generateRecommendations(score, "en");
        var status = com.app.sme_health_backend.recommendation.entity.RecommendationStatus.DONE;
        original.getFirst().setStatus(status);
        original.getFirst().setStatusUpdatedAt(COMPUTED_AT.plusDays(1));
        when(adviceContextService.latest(userId)).thenReturn(Optional.of(new AdviceContext(score, null, "ur", "new-language")));
        when(recommendationRepository.findByUserIdAndMonthOrderByCreatedAtDesc(userId, "2026-09")).thenReturn(original);
        when(recommendationRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        List<Recommendation> refreshed = recommendationService.getRecommendations(userId);
        assertSame(original.getFirst(), refreshed.getFirst());
        assertEquals(status, refreshed.getFirst().getStatus());
        assertEquals(COMPUTED_AT.plusDays(1), refreshed.getFirst().getStatusUpdatedAt());
        assertEquals("ur", refreshed.getFirst().getLanguage());
        verify(recommendationRepository, never()).deleteByUserIdAndMonth(any(), any());
    }

    @Test
    void genuinelyNewFinancialCalculationResetsActionToNew() {
        ScoreResult old = scoreResult("Stable", "cashflow", "0.90");
        List<Recommendation> original = recommendationService.generateRecommendations(old, "en");
        original.getFirst().setStatus(com.app.sme_health_backend.recommendation.entity.RecommendationStatus.DISMISSED);
        original.getFirst().setStatusUpdatedAt(COMPUTED_AT.plusMinutes(1));
        ScoreResult current = scoreResult("Stable", "cashflow", "0.90");
        current.setComputedAt(COMPUTED_AT.plusDays(1));
        when(adviceContextService.latest(userId)).thenReturn(Optional.of(new AdviceContext(current, null, "en", "new-calculation")));
        when(recommendationRepository.findByUserIdAndMonthOrderByCreatedAtDesc(userId, "2026-09")).thenReturn(original);
        when(recommendationRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        Recommendation refreshed = recommendationService.getRecommendations(userId).getFirst();
        assertSame(original.getFirst(), refreshed);
        assertEquals(com.app.sme_health_backend.recommendation.entity.RecommendationStatus.NEW, refreshed.getStatus());
        assertNull(refreshed.getStatusUpdatedAt());
    }

    @Test
    void statusMutationCannotReadAnotherBusinessRecommendation() {
        UUID id = UUID.randomUUID();
        when(recommendationRepository.findMonthByIdAndUserId(id, userId)).thenReturn(Optional.empty());
        assertThrows(com.app.sme_health_backend.shared.exception.ResourceNotFoundException.class,
                () -> recommendationService.updateStatus(userId, id,
                        com.app.sme_health_backend.recommendation.entity.RecommendationStatus.DONE, UUID.randomUUID()));
        verifyNoInteractions(adviceContextService);
        verify(recommendationRepository, never()).save(any());
    }

    @Test
    void statusChangeUsesSameSnapshotLockAndWritesAuditEvent() {
        var audit = mock(com.app.sme_health_backend.audit.service.SecurityAuditService.class);
        var service = new RecommendationService(recommendationRepository, adviceContextService,
                new TranslationService(new ObjectMapper()), audit);
        ScoreResult score = scoreResult("Stable", "cashflow", "0.90");
        List<Recommendation> original = service.generateRecommendations(score, "en");
        original.forEach(item -> item.setSourceVersion("version"));
        Recommendation target = original.getFirst();
        UUID id = UUID.randomUUID(), actor = UUID.randomUUID();
        when(recommendationRepository.findMonthByIdAndUserId(id, userId)).thenReturn(Optional.of("2026-09"));
        when(adviceContextService.forMonth(userId, "2026-09")).thenReturn(Optional.of(new AdviceContext(score, null, "en", "version")));
        when(recommendationRepository.findByUserIdAndMonthOrderByCreatedAtDesc(userId, "2026-09")).thenReturn(original);
        when(recommendationRepository.findScopedForUpdate(id, userId)).thenReturn(Optional.of(target));
        when(recommendationRepository.save(target)).thenReturn(target);
        Recommendation changed = service.updateStatus(userId, id,
                com.app.sme_health_backend.recommendation.entity.RecommendationStatus.DONE, actor);
        assertEquals(com.app.sme_health_backend.recommendation.entity.RecommendationStatus.DONE, changed.getStatus());
        assertNotNull(changed.getStatusUpdatedAt());
        verify(audit).logSuccess(eq(com.app.sme_health_backend.audit.model.AuditEventType.RECOMMENDATION_STATUS_CHANGED),
                eq(actor), isNull(), eq(userId), eq("recommendation"), eq(id.toString()),
                eq(java.util.Map.of("previousStatus", "NEW", "status", "DONE")));
        var order = inOrder(adviceContextService, recommendationRepository);
        order.verify(adviceContextService).forMonth(userId, "2026-09");
        order.verify(recommendationRepository).findScopedForUpdate(id, userId);
    }

    private ScoreResult scoreResult(String band, String weakest, String completeness) {
        ScoreResult result = new ScoreResult();
        result.setId(UUID.randomUUID());
        result.setUserId(userId);
        result.setMonth("2026-09");
        result.setCompositeScore(new BigDecimal("65.00"));
        result.setBand(band);
        result.setDataCompleteness(new BigDecimal(completeness));
        result.setWeakestComponent(weakest);
        result.setComputedAt(COMPUTED_AT);
        result.setComponentScores(new ComponentScoresDto(
                new BigDecimal("55.00"),
                new BigDecimal("40.00"),
                new BigDecimal("70.00"),
                new BigDecimal("60.00"),
                new BigDecimal("80.00")
        ));
        return result;
    }
}
