package com.app.sme_health_backend.shared.advice;

import com.app.sme_health_backend.i18n.TranslationService;
import com.app.sme_health_backend.scoring.dto.ScoreExplanation;
import com.app.sme_health_backend.scoring.entity.ScoreResult;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;
import java.util.stream.Collectors;

/** Uses the persisted calculation snapshot; never substitutes today's profile for historical evidence. */
public final class EvidenceAdvice {
    private final TranslationService translations;
    public EvidenceAdvice(TranslationService translations) { this.translations = translations; }

    public String weakestInsight(ScoreResult score, ScoreResult previous, String language) {
        String key = score.getWeakestComponent();
        BigDecimal value = score.getComponentScores() == null ? null : score.getComponentScores().toMap().get(key);
        String component = translations.translate(language, "component." + key);
        String text = value == null ? translations.translate(language, "insight.focus_weakest_component", Map.of("component", component))
                : t(language, "insight.evidence.weakest", Map.of("component", component, "score", value));
        ScoreExplanation.Component evidence = component(score, key);
        if (evidence == null) return text;
        String buffer = driver(score, "cashflow", "cashBufferMonths");
        if ("cashflow".equals(key) && buffer != null) {
            text += " " + t(language, "insight.evidence.buffer", Map.of("buffer", rounded(buffer), "months", evidence.historyMonthsUsed()));
            String priorBuffer = previous != null && YearMonth.parse(score.getMonth()).minusMonths(1).toString().equals(previous.getMonth())
                    && score.getUserId().equals(previous.getUserId()) ? driver(previous, "cashflow", "cashBufferMonths") : null;
            if (priorBuffer != null) text += " " + t(language, "insight.evidence.buffer_change",
                    Map.of("previous", rounded(priorBuffer), "current", rounded(buffer), "month", previous.getMonth()));
        } else if ("profitability".equals(key)) {
            String margin = driver(score, key, "netMargin");
            String dso = driver(score, key, "dso");
            if (margin != null) text += " " + t(language, "insight.evidence.margin", Map.of("margin", rounded(new BigDecimal(margin).multiply(BigDecimal.valueOf(100)).toString())));
            if (dso != null) text += " " + t(language, "insight.evidence.dso", Map.of("days", rounded(dso)));
        } else if ("repayment".equals(key)) {
            text += " " + t(language, "insight.evidence.self_declared", Map.of());
        } else if ("trend".equals(key)) {
            text += " " + t(language, "insight.evidence.trend", Map.of("months", evidence.historyMonthsUsed()));
        } else if ("compliance".equals(key)) {
            text += " " + t(language, "insight.evidence.compliance", Map.of());
        }
        return text;
    }

    public String dataQuality(ScoreResult score, String language, boolean action) {
        if (score.getExplanation() == null) return null;
        String missing = score.getExplanation().components().entrySet().stream()
                .filter(e -> "UNAVAILABLE".equals(e.getValue().status()))
                .map(e -> translations.translate(language, "component." + e.getKey())).collect(Collectors.joining("، "));
        if (missing.isEmpty()) return t(language, action ? "recommendation.data_quality.complete" : "insight.evidence.complete", Map.of());
        return t(language, action ? "recommendation.evidence.missing" : "insight.evidence.missing", Map.of("components", missing));
    }

    public String action(ScoreResult score, String language) {
        String key = score.getWeakestComponent();
        if (score.getExplanation() == null) return null;
        String buffer = driver(score, "cashflow", "cashBufferMonths");
        if ("cashflow".equals(key) && buffer != null && new BigDecimal(buffer).compareTo(BigDecimal.ONE) < 0)
            return t(language, "recommendation.evidence.buffer", Map.of("buffer", rounded(buffer)));
        if ("profitability".equals(key)) {
            String dso = driver(score, key, "dso");
            String dsoScore = driver(score, key, "dsoScore");
            String marginScore = driver(score, key, "netMarginScore");
            if (dso != null && dsoScore != null && (marginScore == null || new BigDecimal(dsoScore).compareTo(new BigDecimal(marginScore)) <= 0))
                return t(language, "recommendation.evidence.collections", Map.of("days", rounded(dso)));
        }
        if ("repayment".equals(key)) return t(language, "recommendation.evidence.declared_payment", Map.of());
        if ("compliance".equals(key)) return t(language, "recommendation.evidence.registration", Map.of());
        return null;
    }
    public static String driver(ScoreResult score, String component, String key) {
        ScoreExplanation.Component evidence = component(score, component);
        if (evidence == null) return null;
        return evidence.drivers().stream().filter(d -> key.equals(d.key())).map(ScoreExplanation.Driver::value)
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }
    private static ScoreExplanation.Component component(ScoreResult score, String component) {
        return score.getExplanation() == null ? null : score.getExplanation().components().get(component);
    }
    private static String rounded(String value) { return new BigDecimal(value).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(); }
    private String t(String language, String key, Map<String, ?> parameters) { return translations.translate(language, key, parameters); }
}
