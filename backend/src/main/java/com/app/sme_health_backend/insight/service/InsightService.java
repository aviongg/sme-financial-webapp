package com.app.sme_health_backend.insight.service;

import com.app.sme_health_backend.insight.entity.Insight;
import com.app.sme_health_backend.insight.repository.InsightRepository;
import com.app.sme_health_backend.profile.repository.BusinessProfileRepository;
import com.app.sme_health_backend.scoring.dto.ComponentScoresDto;
import com.app.sme_health_backend.scoring.entity.ScoreResult;
import com.app.sme_health_backend.scoring.service.ScoringService;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class InsightService {

    private static final BigDecimal LOW_SCORE_THRESHOLD =
            new BigDecimal("60.00");
    private static final BigDecimal STRONG_SCORE_THRESHOLD =
            new BigDecimal("80.00");
    private static final BigDecimal COMPLETE_DATA_THRESHOLD =
            new BigDecimal("0.80");

    private final InsightRepository insightRepository;
    private final BusinessProfileRepository businessProfileRepository;
    private final ScoringService scoringService;

    public InsightService(
            InsightRepository insightRepository,
            BusinessProfileRepository businessProfileRepository
    ) {
        this(insightRepository, businessProfileRepository, null);
    }

    @Autowired
    public InsightService(
            InsightRepository insightRepository,
            BusinessProfileRepository businessProfileRepository,
            @Autowired(required = false) ScoringService scoringService
    ) {
        this.insightRepository = insightRepository;
        this.businessProfileRepository = businessProfileRepository;
        this.scoringService = scoringService;
    }

    @Transactional
    public List<Insight> getInsights(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }

        if (!businessProfileRepository.existsById(userId)) {
            throw new ResourceNotFoundException(
                    "Business profile not found for this user"
            );
        }

        Optional<ScoreResult> latestScoreOpt = (scoringService != null)
                ? scoringService.getLatestScore(userId)
                : Optional.empty();

        if (latestScoreOpt.isPresent()) {
            ScoreResult realScore = latestScoreOpt.get();
            String targetMonth = realScore.getMonth();

            List<Insight> existingForMonth =
                    insightRepository.findByUserIdAndMonthOrderByCreatedAtDesc(userId, targetMonth);

            boolean isMockOrStale = existingForMonth.size() < 3
                    || existingForMonth.stream().map(Insight::getCategory).distinct().count() < 3
                    || existingForMonth.stream().anyMatch(i -> isLegacyMockCategory(i.getCategory()))
                    || (realScore.getComputedAt() != null
                    && existingForMonth.get(0).getCreatedAt() != null
                    && existingForMonth.get(0).getCreatedAt().isBefore(realScore.getComputedAt()));

            if (!isMockOrStale) {
                return existingForMonth;
            }

            List<Insight> allUserInsights =
                    insightRepository.findByUserIdOrderByCreatedAtDesc(userId);
            List<Insight> toRemove = allUserInsights.stream()
                    .filter(i -> targetMonth.equals(i.getMonth()) || isLegacyMockCategory(i.getCategory()))
                    .toList();
            if (!toRemove.isEmpty()) {
                insightRepository.deleteAll(toRemove);
            }

            List<Insight> newInsights = generateInsights(realScore);
            return insightRepository.saveAll(newInsights);
        }

        List<Insight> existingInsights =
                insightRepository.findByUserIdOrderByCreatedAtDesc(userId);

        if (!existingInsights.isEmpty()) {
            return existingInsights;
        }

        return insightRepository.saveAll(
                generateInsights(createMockScoreResult(userId))
        );
    }

    private boolean isLegacyMockCategory(String category) {
        return "liquidity".equalsIgnoreCase(category) || "leverage".equalsIgnoreCase(category);
    }

    public List<Insight> generateInsights(ScoreResult scoreResult) {
        validateScoreResult(scoreResult);

        List<Insight> insights = new ArrayList<>();
        String weakestComponent = displayName(
                scoreResult.getWeakestComponent()
        );

        insights.add(createInsight(
                scoreResult,
                "Focus first on " + weakestComponent
                        + ", which is currently your weakest financial area.",
                scoreResult.getWeakestComponent(),
                "high"
        ));

        insights.add(createInsight(
                scoreResult,
                overallScoreText(scoreResult),
                "overall_health",
                overallScorePriority(scoreResult)
        ));

        if (scoreResult.getDataCompleteness()
                .compareTo(COMPLETE_DATA_THRESHOLD) < 0) {
            insights.add(createInsight(
                    scoreResult,
                    "Add the missing monthly financial records to improve"
                            + " the confidence of your financial health score.",
                    "data_quality",
                    "high"
            ));
        } else {
            insights.add(createInsight(
                    scoreResult,
                    "Your available financial records provide a solid base"
                            + " for monitoring business health over time.",
                    "data_quality",
                    "low"
            ));
        }

        return insights;
    }

    private ScoreResult createMockScoreResult(UUID userId) {
        ScoreResult result = new ScoreResult();
        result.setUserId(userId);
        result.setMonth(YearMonth.now().toString());
        result.setCompositeScore(new BigDecimal("72.00"));
        result.setBand("good");
        result.setComponentScores(ComponentScoresDto.fromMap(Map.of(
                "liquidity", new BigDecimal("58.00"),
                "profitability", new BigDecimal("76.00"),
                "leverage", new BigDecimal("84.00")
        )));
        result.setWeakestComponent("liquidity");
        result.setDataCompleteness(new BigDecimal("0.90"));
        result.setComputedAt(LocalDateTime.now());
        return result;
    }

    private Insight createInsight(
            ScoreResult scoreResult,
            String text,
            String category,
            String priority
    ) {
        Insight insight = new Insight();

        insight.setUserId(scoreResult.getUserId());
        insight.setMonth(scoreResult.getMonth());
        insight.setText(text);
        insight.setCategory(category);
        insight.setPriority(priority);
        insight.setCreatedAt(LocalDateTime.now());

        return insight;
    }

    private String overallScoreText(ScoreResult scoreResult) {
        BigDecimal score = scoreResult.getCompositeScore();

        if (score.compareTo(LOW_SCORE_THRESHOLD) < 0) {
            return "Your overall financial score is " + score
                    + ". Prioritize the high-priority action first and"
                    + " review the score again after the next month.";
        }

        if (score.compareTo(STRONG_SCORE_THRESHOLD) >= 0) {
            return "Your overall financial score is " + score
                    + ". Keep the current routines that are supporting"
                    + " your financial health.";
        }

        return "Your overall financial score is " + score
                + ". Keep monitoring the key components each month"
                + " to build on this position.";
    }

    private String overallScorePriority(ScoreResult scoreResult) {
        BigDecimal score = scoreResult.getCompositeScore();

        if (score.compareTo(LOW_SCORE_THRESHOLD) < 0) {
            return "high";
        }

        if (score.compareTo(STRONG_SCORE_THRESHOLD) >= 0) {
            return "low";
        }

        return "medium";
    }

    private String displayName(String component) {
        if (component == null || component.isBlank()) {
            return "the weakest component";
        }

        return component.replace('_', ' ');
    }

    private void validateScoreResult(ScoreResult scoreResult) {
        if (scoreResult == null) {
            throw new IllegalArgumentException("Score result is required");
        }

        if (scoreResult.getUserId() == null) {
            throw new IllegalArgumentException("Score result user ID is required");
        }

        if (scoreResult.getMonth() == null || scoreResult.getMonth().isBlank()) {
            throw new IllegalArgumentException("Score result month is required");
        }

        if (scoreResult.getCompositeScore() == null) {
            throw new IllegalArgumentException(
                    "Score result composite score is required"
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
