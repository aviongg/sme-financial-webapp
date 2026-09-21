package com.app.sme_health_backend.recommendation.service;

import com.app.i18n.TranslationService;
import com.app.sme_health_backend.recommendation.entity.Recommendation;
import com.app.sme_health_backend.recommendation.repository.RecommendationRepository;
import com.app.sme_health_backend.score.dto.ScoreResult;
import com.app.sme_health_backend.shared.advice.AdviceContext;
import com.app.sme_health_backend.shared.advice.AdviceContextService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class RecommendationService {

    private static final BigDecimal LOW_SCORE_THRESHOLD =
            new BigDecimal("60.00");
    private static final BigDecimal STRONG_SCORE_THRESHOLD =
            new BigDecimal("80.00");
    private static final BigDecimal COMPLETE_DATA_THRESHOLD =
            new BigDecimal("0.80");

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

    @Transactional
    public List<Recommendation> getRecommendations(UUID userId) {
        return getRecommendations(userId, null);
    }

    /** Returns advice for the requested month, or the latest persisted score. */
    @Transactional
    public List<Recommendation> getRecommendations(UUID userId, String month) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        return (month == null
                ? adviceContextService.latest(userId)
                : adviceContextService.forMonth(userId, month))
                .map(this::refreshRecommendations)
                .orElseGet(List::of);
    }

    @Transactional
    public List<Recommendation> generateAndSaveRecommendations(
            ScoreResult scoreResult
    ) {
        validateScoreResult(scoreResult);
        return refreshRecommendations(adviceContextService.forScore(scoreResult));
    }

    private List<Recommendation> refreshRecommendations(AdviceContext context) {
        ScoreResult score = context.score();
        List<Recommendation> expected = generateRecommendations(score, context.language());
        expected.forEach(recommendation -> {
            recommendation.setSourceVersion(context.sourceVersion());
            recommendation.setLanguage(context.language());
            recommendation.setSourceComputedAt(score.getComputedAt());
        });
        List<Recommendation> stored = recommendationRepository
                .findByUserIdAndMonthOrderByCreatedAtDesc(score.getUserId(), score.getMonth());

        // Compare every category and its content, so legacy or partial rows cannot
        // conceal missing advice. Return existing rows to retain stable IDs.
        if (stored.size() == expected.size()
                && expected.stream().allMatch(wanted -> stored.stream()
                        .filter(actual -> sameRecommendation(actual, wanted)).count() == 1)) {
            return expected.stream().map(wanted -> stored.stream()
                    .filter(actual -> Objects.equals(actual.getCategory(), wanted.getCategory()))
                    .findFirst().orElseThrow()).toList();
        }

        recommendationRepository.deleteByUserIdAndMonth(score.getUserId(), score.getMonth());
        // Hibernate may otherwise insert new rows before executing deferred deletes.
        recommendationRepository.flush();
        return recommendationRepository.saveAll(expected);
    }

    private boolean sameRecommendation(Recommendation actual, Recommendation expected) {
        return Objects.equals(actual.getUserId(), expected.getUserId())
                && Objects.equals(actual.getMonth(), expected.getMonth())
                && Objects.equals(actual.getCategory(), expected.getCategory())
                && Objects.equals(actual.getText(), expected.getText())
                && Objects.equals(actual.getPriority(), expected.getPriority())
                && Objects.equals(actual.getLanguage(), expected.getLanguage())
                && Objects.equals(actual.getSourceVersion(), expected.getSourceVersion())
                && Objects.equals(actual.getSourceComputedAt(), expected.getSourceComputedAt());
    }

    public List<Recommendation> generateRecommendations(
            ScoreResult scoreResult
    ) {
        return generateRecommendations(
                scoreResult,
                TranslationService.DEFAULT_LANGUAGE
        );
    }

    public List<Recommendation> generateRecommendations(
            ScoreResult scoreResult,
            String language
    ) {
        validateScoreResult(scoreResult);

        List<Recommendation> recommendations = new ArrayList<>();
        String selectedLanguage = translationService.resolveLanguage(language);
        String weakestComponent = scoreResult.getWeakestComponent();
        BigDecimal weakestScore = componentScore(
                scoreResult.getComponentScores(),
                weakestComponent
        );

        recommendations.add(createRecommendation(
                scoreResult,
                weakestComponentText(
                        selectedLanguage,
                        weakestComponent,
                        weakestScore
                ),
                weakestComponent,
                componentPriority(weakestScore, scoreResult.getBand())
        ));

        recommendations.add(createRecommendation(
                scoreResult,
                overallBandText(selectedLanguage, scoreResult.getBand()),
                "overall_health",
                bandPriority(scoreResult.getBand())
        ));

        if (scoreResult.getDataCompleteness()
                .compareTo(COMPLETE_DATA_THRESHOLD) < 0) {
            recommendations.add(createRecommendation(
                    scoreResult,
                    translationService.translate(
                            selectedLanguage,
                            "recommendation.data_quality.incomplete"
                    ),
                    "data_quality",
                    "high"
            ));
        } else {
            recommendations.add(createRecommendation(
                    scoreResult,
                    translationService.translate(
                            selectedLanguage,
                            "recommendation.data_quality.complete"
                    ),
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
            case "strong" ->
                    translationService.translate(
                            language,
                            "recommendation.overall.strong"
                    );
            case "stable" ->
                    translationService.translate(
                            language,
                            "recommendation.overall.stable"
                    );
            case "needs_attention" ->
                    translationService.translate(
                            language,
                            "recommendation.overall.needs_attention"
                    );
            case "at_risk" ->
                    translationService.translate(
                            language,
                            "recommendation.overall.at_risk"
                    );
            default ->
                    translationService.translate(
                            language,
                            "recommendation.overall.unknown",
                            Map.of("band", band)
                    );
        };
    }

    private String componentPriority(
            BigDecimal componentScore,
            String band
    ) {
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

    private BigDecimal componentScore(
            Map<String, BigDecimal> componentScores,
            String component
    ) {
        BigDecimal exactScore = componentScores.get(component);
        if (exactScore != null) {
            return exactScore;
        }

        for (Map.Entry<String, BigDecimal> entry : componentScores.entrySet()) {
            if (entry.getKey() != null
                    && entry.getKey().equalsIgnoreCase(component)) {
                return entry.getValue();
            }
        }

        return null;
    }

    private String normalizedBand(String band) {
        return band.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private String componentAction(String language, String component) {
        String normalizedComponent = normalizedComponent(component);
        String key = switch (normalizedComponent) {
            case "cashflow" ->
                    "recommendation.cashflow.improve";
            case "profitability" ->
                    "recommendation.profitability.improve";
            case "repayment" ->
                    "recommendation.repayment.improve";
            case "trend" ->
                    "recommendation.trend.improve";
            case "compliance" ->
                    "recommendation.compliance.improve";
            default ->
                    "recommendation.default.improve";
        };

        return translationService.translate(language, key);
    }

    private String displayName(String language, String component) {
        if (component == null || component.isBlank()) {
            return translationService.translate(
                    language,
                    "component.weakest_component"
            );
        }

        String key = "component." + normalizedComponent(component);

        if (translationService.hasKey(key)) {
            return translationService.translate(language, key);
        }

        return component.replace('_', ' ');
    }

    private String normalizedComponent(String component) {
        return component.trim().toLowerCase(Locale.ROOT);
    }

    private void validateScoreResult(ScoreResult scoreResult) {
        if (scoreResult == null) {
            throw new IllegalArgumentException("Score result is required");
        }

        scoreResult.validate();
    }
}
