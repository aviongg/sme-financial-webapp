package com.app.sme_health_backend.recommendation.service;

import com.app.sme_health_backend.i18n.TranslationService;
import com.app.sme_health_backend.recommendation.entity.Recommendation;
import com.app.sme_health_backend.recommendation.repository.RecommendationRepository;
import com.app.sme_health_backend.scoring.entity.ScoreResult;
import com.app.sme_health_backend.shared.advice.AdviceContext;
import com.app.sme_health_backend.shared.advice.AdviceContextService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class RecommendationService {

    private static final BigDecimal COMPLETE_DATA_THRESHOLD =
            new BigDecimal("0.80");
    private static final BigDecimal LOW_SCORE_THRESHOLD =
            new BigDecimal("60.00");
    private static final BigDecimal STRONG_SCORE_THRESHOLD =
            new BigDecimal("80.00");

    private final RecommendationRepository recommendationRepository;
    private final AdviceContextService adviceContextService;
    private final TranslationService translationService;

    public RecommendationService(
            RecommendationRepository recommendationRepository,
            AdviceContextService adviceContextService,
            TranslationService translationService
    ) {
        this.recommendationRepository = recommendationRepository;
        this.adviceContextService = adviceContextService;
        this.translationService = translationService;
    }

    /** Returns advice for the latest persisted score; absent scores have no advice. */
    @Transactional
    public List<Recommendation> getRecommendations(UUID userId) {
        validateUserId(userId);
        return adviceContextService.latest(userId)
                .map(this::refreshRecommendations)
                .orElseGet(List::of);
    }

    /** An explicit month never falls back to a different month's score. */
    @Transactional
    public List<Recommendation> getRecommendations(UUID userId, String month) {
        if (month == null) {
            return getRecommendations(userId);
        }
        validateUserId(userId);
        return adviceContextService.forMonth(userId, month)
                .map(this::refreshRecommendations)
                .orElseGet(List::of);
    }

    @Transactional
    public List<Recommendation> generateAndSaveRecommendations(ScoreResult scoreResult) {
        validateScoreResult(scoreResult);
        return refreshRecommendations(adviceContextService.forScore(scoreResult));
    }

    private List<Recommendation> refreshRecommendations(AdviceContext context) {
        ScoreResult score = context.score();
        List<Recommendation> expected = generateRecommendations(score, context.language());
        expected.forEach(recommendation -> recommendation.setSourceVersion(context.sourceVersion()));
        List<Recommendation> existing = recommendationRepository.findByUserIdAndMonthOrderByCreatedAtDesc(
                score.getUserId(), score.getMonth());

        List<Recommendation> matching = matchingRecommendations(existing, expected);
        if (matching != null) {
            return matching;
        }

        // Shared context holds a per-profile lock through this transaction.
        // Flush deletions before inserting the replacement unique category set.
        recommendationRepository.deleteByUserIdAndMonth(score.getUserId(), score.getMonth());
        recommendationRepository.flush();
        return recommendationRepository.saveAll(expected);
    }

    private List<Recommendation> matchingRecommendations(
            List<Recommendation> existing,
            List<Recommendation> expected
    ) {
        if (existing.size() != expected.size()) {
            return null;
        }
        Map<String, Recommendation> byCategory = new HashMap<>();
        for (Recommendation recommendation : existing) {
            if (byCategory.put(recommendation.getCategory(), recommendation) != null) {
                return null;
            }
        }
        List<Recommendation> ordered = new ArrayList<>();
        for (Recommendation wanted : expected) {
            Recommendation stored = byCategory.get(wanted.getCategory());
            if (stored == null
                    || !Objects.equals(stored.getUserId(), wanted.getUserId())
                    || !Objects.equals(stored.getMonth(), wanted.getMonth())
                    || !Objects.equals(stored.getText(), wanted.getText())
                    || !Objects.equals(stored.getPriority(), wanted.getPriority())
                    || !Objects.equals(stored.getLanguage(), wanted.getLanguage())
                    || !Objects.equals(stored.getSourceVersion(), wanted.getSourceVersion())
                    || !Objects.equals(stored.getSourceComputedAt(), wanted.getSourceComputedAt())) {
                return null;
            }
            ordered.add(stored);
        }
        return ordered;
    }

    public List<Recommendation> generateRecommendations(ScoreResult scoreResult) {
        return generateRecommendations(scoreResult, TranslationService.DEFAULT_LANGUAGE);
    }

    public List<Recommendation> generateRecommendations(ScoreResult scoreResult, String language) {
        validateScoreResult(scoreResult);

        List<Recommendation> recommendations = new ArrayList<>();
        String selectedLanguage = translationService.resolveLanguage(language);
        String weakestComponent = scoreResult.getWeakestComponent();
        Map<String, BigDecimal> componentScores = scoreResult.getComponentScores() != null
                ? scoreResult.getComponentScores().toMap()
                : Map.of();
        BigDecimal weakestScore = componentScores.get(weakestComponent);

        recommendations.add(createRecommendation(
                scoreResult,
                weakestComponentText(selectedLanguage, weakestComponent, weakestScore),
                weakestComponent,
                componentPriority(weakestScore, scoreResult.getBand())
        ));

        recommendations.add(createRecommendation(
                scoreResult,
                overallBandText(selectedLanguage, scoreResult.getBand()),
                "overall_health",
                bandPriority(scoreResult.getBand())
        ));

        if (scoreResult.getDataCompleteness().compareTo(COMPLETE_DATA_THRESHOLD) < 0) {
            recommendations.add(createRecommendation(
                    scoreResult,
                    translationService.translate(selectedLanguage, "recommendation.data_quality.incomplete"),
                    "data_quality",
                    "high"
            ));
        } else {
            recommendations.add(createRecommendation(
                    scoreResult,
                    translationService.translate(selectedLanguage, "recommendation.data_quality.complete"),
                    "data_quality",
                    "low"
            ));
        }

        recommendations.forEach(recommendation -> {
            recommendation.setLanguage(selectedLanguage);
            recommendation.setSourceComputedAt(scoreResult.getComputedAt());
        });
        return recommendations;
    }

    private Recommendation createRecommendation(
            ScoreResult scoreResult,
            String text,
            String category,
            String priority
    ) {
        Recommendation recommendation = new Recommendation();
        recommendation.setUserId(scoreResult.getUserId());
        recommendation.setMonth(scoreResult.getMonth());
        recommendation.setText(text);
        recommendation.setCategory(category);
        recommendation.setPriority(priority);
        recommendation.setCreatedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
        return recommendation;
    }

    private String weakestComponentText(
            String language,
            String component,
            BigDecimal componentScore
    ) {
        Map<String, Object> parameters = Map.of(
                "component", displayName(language, component),
                "action", componentAction(language, component),
                "score", componentScore == null ? "" : componentScore
        );

        if (componentScore == null) {
            return translationService.translate(
                    language,
                    "recommendation.focus_component",
                    parameters
            );
        }

        return translationService.translate(
                language,
                "recommendation.focus_component_with_score",
                parameters
        );
    }

    private String overallBandText(String language, String band) {
        return switch (normalizedBand(band)) {
            case "strong" -> translationService.translate(language, "recommendation.overall.strong");
            case "stable" -> translationService.translate(language, "recommendation.overall.stable");
            case "needs_attention" -> translationService.translate(language, "recommendation.overall.needs_attention");
            case "at_risk" -> translationService.translate(language, "recommendation.overall.at_risk");
            default -> translationService.translate(language, "recommendation.overall.unknown",
                    Map.of("band", band == null ? "" : band));
        };
    }

    private String componentPriority(BigDecimal componentScore, String band) {
        if (componentScore == null) {
            return bandPriority(band);
        }
        if (componentScore.compareTo(LOW_SCORE_THRESHOLD) < 0) {
            return "high";
        }
        if (componentScore.compareTo(STRONG_SCORE_THRESHOLD) >= 0) {
            return "low";
        }
        return "medium";
    }

    private String bandPriority(String band) {
        return switch (normalizedBand(band)) {
            case "at_risk", "needs_attention" -> "high";
            case "strong" -> "low";
            default -> "medium";
        };
    }

    private String normalizedBand(String band) {
        return band == null ? "" : band.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private String componentAction(String language, String component) {
        String normalizedComponent = normalizedComponent(component);
        String key = switch (normalizedComponent) {
            case "cashflow" -> "recommendation.cashflow.improve";
            case "profitability" -> "recommendation.profitability.improve";
            case "repayment" -> "recommendation.repayment.improve";
            case "trend" -> "recommendation.trend.improve";
            case "compliance" -> "recommendation.compliance.improve";
            default -> "recommendation.default.improve";
        };
        return translationService.translate(language, key);
    }

    private String displayName(String language, String component) {
        if (component == null || component.isBlank()) {
            return translationService.translate(language, "component.weakest_component");
        }
        String key = "component." + normalizedComponent(component);
        if (translationService.hasKey(key)) {
            return translationService.translate(language, key);
        }
        return component.replace('_', ' ');
    }

    private String normalizedComponent(String component) {
        return component == null ? "" : component.trim().toLowerCase(Locale.ROOT);
    }

    private void validateScoreResult(ScoreResult scoreResult) {
        if (scoreResult == null) {
            throw new IllegalArgumentException("Score result is required");
        }
        if (scoreResult.getUserId() == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        if (scoreResult.getMonth() == null) {
            throw new IllegalArgumentException("Month is required");
        }
    }

    private void validateUserId(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }
    }
}
