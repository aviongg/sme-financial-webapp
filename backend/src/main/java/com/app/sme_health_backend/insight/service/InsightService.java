package com.app.sme_health_backend.insight.service;

import com.app.sme_health_backend.i18n.TranslationService;
import com.app.sme_health_backend.insight.entity.Insight;
import com.app.sme_health_backend.insight.repository.InsightRepository;
import com.app.sme_health_backend.scoring.entity.ScoreResult;
import com.app.sme_health_backend.shared.advice.AdviceContext;
import com.app.sme_health_backend.shared.advice.AdviceContextService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class InsightService {

    private static final BigDecimal COMPLETE_DATA_THRESHOLD =
            new BigDecimal("0.80");

    private final InsightRepository insightRepository;
    private final AdviceContextService adviceContextService;
    private final TranslationService translationService;

    public InsightService(
            InsightRepository insightRepository,
            AdviceContextService adviceContextService,
            TranslationService translationService
    ) {
        this.insightRepository = insightRepository;
        this.adviceContextService = adviceContextService;
        this.translationService = translationService;
    }

    /** Returns advice for the latest persisted score; absent scores have no advice. */
    @Transactional
    public List<Insight> getInsights(UUID userId) {
        validateUserId(userId);
        return adviceContextService.latest(userId)
                .map(this::refreshInsights)
                .orElseGet(List::of);
    }

    /** An explicit month never falls back to a different month's score. */
    @Transactional
    public List<Insight> getInsights(UUID userId, String month) {
        if (month == null) {
            return getInsights(userId);
        }
        validateUserId(userId);
        return adviceContextService.forMonth(userId, month)
                .map(this::refreshInsights)
                .orElseGet(List::of);
    }

    @Transactional
    public List<Insight> generateAndSaveInsights(ScoreResult scoreResult) {
        validateScoreResult(scoreResult);
        return refreshInsights(adviceContextService.forScore(scoreResult));
    }

    private List<Insight> refreshInsights(AdviceContext context) {
        ScoreResult score = context.score();
        List<Insight> expected = generateInsights(score, context.language(), context.previousScore());
        expected.forEach(insight -> insight.setSourceVersion(context.sourceVersion()));
        List<Insight> existing = insightRepository.findByUserIdAndMonthOrderByCreatedAtDesc(
                score.getUserId(), score.getMonth());

        List<Insight> matching = matchingInsights(existing, expected);
        if (matching != null) {
            return matching;
        }

        // Shared context holds a per-profile lock through this transaction.
        // Flush deletions before inserting the replacement unique category set.
        insightRepository.deleteByUserIdAndMonth(score.getUserId(), score.getMonth());
        insightRepository.flush();
        return insightRepository.saveAll(expected);
    }

    private List<Insight> matchingInsights(List<Insight> existing, List<Insight> expected) {
        if (existing.size() != expected.size()) {
            return null;
        }
        Map<String, Insight> byCategory = new HashMap<>();
        for (Insight insight : existing) {
            if (byCategory.put(insight.getCategory(), insight) != null) {
                return null;
            }
        }
        List<Insight> ordered = new ArrayList<>();
        for (Insight wanted : expected) {
            Insight stored = byCategory.get(wanted.getCategory());
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

    public List<Insight> generateInsights(ScoreResult scoreResult) {
        return generateInsights(scoreResult, TranslationService.DEFAULT_LANGUAGE, null);
    }

    public List<Insight> generateInsights(ScoreResult scoreResult, String language) {
        return generateInsights(scoreResult, language, null);
    }

    /** Pure generation consumes the agreed score contract without recalculating scores. */
    public List<Insight> generateInsights(
            ScoreResult scoreResult,
            String language,
            ScoreResult previousScore
    ) {
        validateScoreResult(scoreResult);
        if (previousScore != null) {
            validateScoreResult(previousScore);
            if (!scoreResult.getUserId().equals(previousScore.getUserId())) {
                throw new IllegalArgumentException("Previous score must belong to the same user");
            }
        }
        String selectedLanguage = translationService.resolveLanguage(language);
        LocalDateTime createdAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        List<Insight> insights = new ArrayList<>();

        insights.add(createInsight(scoreResult, selectedLanguage, createdAt,
                translationService.translate(selectedLanguage, "insight.focus_weakest_component",
                        Map.of("component", translationService.translate(selectedLanguage,
                                "component." + scoreResult.getWeakestComponent()))),
                scoreResult.getWeakestComponent(), "high"));

        String overallKey = switch (scoreResult.getBand()) {
            case "Strong" -> "insight.overall.strong";
            case "Stable" -> "insight.overall.stable";
            default -> "insight.overall.low";
        };
        String overallPriority = switch (scoreResult.getBand()) {
            case "Strong" -> "low";
            case "Stable" -> "medium";
            default -> "high";
        };
        insights.add(createInsight(scoreResult, selectedLanguage, createdAt,
                translationService.translate(selectedLanguage, overallKey,
                        Map.of("score", scoreResult.getCompositeScore())),
                "overall_health", overallPriority));

        boolean complete = scoreResult.getDataCompleteness().compareTo(COMPLETE_DATA_THRESHOLD) >= 0;
        insights.add(createInsight(scoreResult, selectedLanguage, createdAt,
                translationService.translate(selectedLanguage, complete
                        ? "insight.data_quality.complete" : "insight.data_quality.incomplete"),
                "data_quality", complete ? "low" : "high"));

        if (previousScore != null && YearMonth.parse(scoreResult.getMonth()).minusMonths(1)
                .equals(YearMonth.parse(previousScore.getMonth()))) {
            BigDecimal delta = scoreResult.getCompositeScore().subtract(previousScore.getCompositeScore());
            String direction = delta.signum() > 0 ? "improved" : delta.signum() < 0 ? "declined" : "unchanged";
            insights.add(createInsight(scoreResult, selectedLanguage, createdAt,
                    translationService.translate(selectedLanguage, "insight.monthly_change." + direction,
                            Map.of("month", previousScore.getMonth(),
                                    "delta", delta.abs().stripTrailingZeros().toPlainString(),
                                    "score", scoreResult.getCompositeScore())),
                    "monthly_change", delta.signum() < 0 ? "high" : "low"));
        }
        return insights;
    }

    private Insight createInsight(ScoreResult score, String language, LocalDateTime createdAt,
                                  String text, String category, String priority) {
        Insight insight = new Insight();
        insight.setUserId(score.getUserId());
        insight.setMonth(score.getMonth());
        insight.setText(text);
        insight.setCategory(category);
        insight.setPriority(priority);
        insight.setCreatedAt(createdAt);
        insight.setLanguage(language);
        insight.setSourceComputedAt(score.getComputedAt());
        return insight;
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
