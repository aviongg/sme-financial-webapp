package com.app.sme_health_backend.recommendation.service;

import com.app.sme_health_backend.profile.repository.BusinessProfileRepository;
import com.app.sme_health_backend.recommendation.entity.Recommendation;
import com.app.sme_health_backend.recommendation.repository.RecommendationRepository;
import com.app.sme_health_backend.scoring.dto.ComponentScoresDto;
import com.app.sme_health_backend.scoring.entity.ScoreResult;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private final BusinessProfileRepository businessProfileRepository;

    public RecommendationService(
            RecommendationRepository recommendationRepository,
            BusinessProfileRepository businessProfileRepository
    ) {
        this.recommendationRepository = recommendationRepository;
        this.businessProfileRepository = businessProfileRepository;
    }

    @Transactional
    public List<Recommendation> getRecommendations(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }

        if (!businessProfileRepository.existsById(userId)) {
            throw new ResourceNotFoundException(
                    "Business profile not found for this user"
            );
        }

        List<Recommendation> existingRecommendations =
                recommendationRepository
                        .findByUserIdOrderByCreatedAtDesc(userId);

        if (!existingRecommendations.isEmpty()) {
            return existingRecommendations;
        }

        return recommendationRepository.saveAll(
                generateRecommendations(createMockScoreResult(userId))
        );
    }

    public List<Recommendation> generateRecommendations(
            ScoreResult scoreResult
    ) {
        validateScoreResult(scoreResult);

        List<Recommendation> recommendations = new ArrayList<>();
        String weakestComponent = scoreResult.getWeakestComponent();
        Map<String, BigDecimal> scoresMap = scoreResult.getComponentScores() != null
                ? scoreResult.getComponentScores().toMap()
                : Collections.emptyMap();
        BigDecimal weakestScore = componentScore(
                scoresMap,
                weakestComponent
        );

        recommendations.add(createRecommendation(
                scoreResult,
                weakestComponentText(weakestComponent, weakestScore),
                weakestComponent,
                componentPriority(weakestScore, scoreResult.getBand())
        ));

        recommendations.add(createRecommendation(
                scoreResult,
                overallBandText(scoreResult.getBand()),
                "overall_health",
                bandPriority(scoreResult.getBand())
        ));

        if (scoreResult.getDataCompleteness()
                .compareTo(COMPLETE_DATA_THRESHOLD) < 0) {
            recommendations.add(createRecommendation(
                    scoreResult,
                    "Complete the missing monthly financial inputs before"
                            + " relying on this score for important decisions.",
                    "data_quality",
                    "high"
            ));
        } else {
            recommendations.add(createRecommendation(
                    scoreResult,
                    "Keep monthly financial records complete so future"
                            + " recommendations remain reliable.",
                    "data_quality",
                    "low"
            ));
        }

        return recommendations;
    }

    private ScoreResult createMockScoreResult(UUID userId) {
        ScoreResult result = new ScoreResult();
        result.setUserId(userId);
        result.setMonth(YearMonth.now().toString());
        result.setCompositeScore(new BigDecimal("72.00"));
        result.setBand("Stable");
        result.setComponentScores(new ComponentScoresDto(
                new BigDecimal("58.00"),
                new BigDecimal("76.00"),
                new BigDecimal("72.00"),
                new BigDecimal("70.00"),
                new BigDecimal("88.00")
        ));
        result.setWeakestComponent("cashflow");
        result.setDataCompleteness(new BigDecimal("0.90"));
        result.setComputedAt(LocalDateTime.now());
        return result;
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
        recommendation.setCreatedAt(LocalDateTime.now());

        return recommendation;
    }

    private String weakestComponentText(
            String component,
            BigDecimal componentScore
    ) {
        String componentName = displayName(component);
        String scoreText = componentScore == null
                ? ""
                : " Its current component score is " + componentScore + ".";

        String action = switch (component.toLowerCase(Locale.ROOT)) {
            case "cashflow", "liquidity" ->
                    "Review collections, payment timing, and cash reserved"
                            + " for near-term obligations.";
            case "profitability" ->
                    "Review pricing, cost of goods, and operating expenses"
                            + " to protect margins.";
            case "repayment", "leverage" ->
                    "Track upcoming repayments and avoid taking on new debt"
                            + " until coverage improves.";
            case "trend" ->
                    "Compare recent months and act early if revenue or"
                            + " cash balance is slipping.";
            case "compliance" ->
                    "Keep monthly records and required documents complete"
                            + " so the score reflects the business accurately.";
            default ->
                    "Review the underlying monthly records and set one"
                            + " measurable improvement action.";
        };

        return "Prioritize " + componentName + ". " + action + scoreText;
    }

    private String overallBandText(String band) {
        return switch (normalizedBand(band)) {
            case "strong" ->
                    "Your financial health is Strong. Continue the routines"
                            + " supporting the component scores and review"
                            + " them monthly.";
            case "stable", "good" ->
                    "Your financial health is Stable. Keep monitoring the"
                            + " component scores and address small changes"
                            + " early.";
            case "needs_attention" ->
                    "Your financial health Needs Attention. Start with the"
                            + " weakest component and review progress monthly.";
            case "at_risk", "vulnerable" ->
                    "Your financial health is At Risk. Address the highest"
                            + " priority action before taking on new financial"
                            + " commitments.";
            default ->
                    "Your current financial health band is " + band
                            + ". Start with the weakest component and monitor"
                            + " progress monthly.";
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
            case "at_risk", "needs_attention", "vulnerable" -> "high";
            case "strong" -> "low";
            default -> "medium";
        };
    }

    private BigDecimal componentScore(
            Map<String, BigDecimal> componentScores,
            String component
    ) {
        if (componentScores == null || component == null) {
            return null;
        }

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
        if (band == null) {
            return "";
        }
        return band.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private String displayName(String component) {
        if (component == null) {
            return "";
        }
        return component.replace('_', ' ');
    }

    private void validateScoreResult(ScoreResult scoreResult) {
        if (scoreResult == null) {
            throw new IllegalArgumentException("Score result is required");
        }

        if (scoreResult.getUserId() == null) {
            throw new IllegalArgumentException(
                    "Score result user ID is required"
            );
        }

        if (scoreResult.getMonth() == null || scoreResult.getMonth().isBlank()) {
            throw new IllegalArgumentException(
                    "Score result month is required"
            );
        }

        if (scoreResult.getBand() == null || scoreResult.getBand().isBlank()) {
            throw new IllegalArgumentException("Score result band is required");
        }

        if (scoreResult.getComponentScores() == null) {
            throw new IllegalArgumentException(
                    "Score result component scores are required"
            );
        }

        if (scoreResult.getWeakestComponent() == null
                || scoreResult.getWeakestComponent().isBlank()) {
            throw new IllegalArgumentException(
                    "Score result weakest component is required"
            );
        }

        if (scoreResult.getDataCompleteness() == null) {
            throw new IllegalArgumentException(
                    "Score result data completeness is required"
            );
        }
    }
}
