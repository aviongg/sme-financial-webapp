package com.app.sme_health_backend.shared.advice;

import com.app.sme_health_backend.i18n.TranslationService;
import com.app.sme_health_backend.scoring.dto.ComponentScoresDto;
import com.app.sme_health_backend.scoring.dto.ScoreExplanation;
import com.app.sme_health_backend.scoring.entity.ScoreResult;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EvidenceAdviceTests {
    private final EvidenceAdvice advice = new EvidenceAdvice(new TranslationService(new ObjectMapper()));

    @ParameterizedTest @ValueSource(strings = {"en", "ur"})
    void bufferAdviceUsesRealSnapshotAndExactPriorCalendarMonth(String language) {
        ScoreResult score = score("2026-03", "cashflow", "0.5");
        ScoreResult prior = score("2026-02", "cashflow", "2.25"); prior.setUserId(score.getUserId());
        String insight = advice.weakestInsight(score, prior, language);
        assertTrue(insight.contains("54")); assertTrue(insight.contains("0.5")); assertTrue(insight.contains("2.25"));
        assertTrue(insight.contains("2026-02")); assertFalse(insight.contains("{"));
        assertTrue(advice.action(score, language).contains("0.5"));
        prior.setMonth("2026-01");
        assertFalse(advice.weakestInsight(score, prior, language).contains("2.25"));
    }

    @ParameterizedTest @ValueSource(strings = {"en", "ur"})
    void unavailableEvidenceNeverBecomesInventedNumericalAdvice(String language) {
        ScoreResult score = score("2026-03", "cashflow", null);
        String insight = advice.weakestInsight(score, null, language);
        assertFalse(insight.contains("null")); assertFalse(insight.contains("{"));
        assertNull(advice.action(score, language));
        String missing = advice.dataQuality(score, language, true);
        assertFalse(missing.contains("{")); assertFalse(missing.isBlank());
    }

    @ParameterizedTest @ValueSource(strings = {"en", "ur"})
    void repaymentIsExplicitlySelfDeclaredAndNotObserved(String language) {
        ScoreResult score = score("2026-03", "repayment", null);
        String insight = advice.weakestInsight(score, null, language);
        assertTrue(insight.contains(language.equals("en") ? "self-declared" : "خود"));
        assertTrue(insight.contains(language.equals("en") ? "not observed" : "نہیں"));
    }

    private static ScoreResult score(String month, String weakest, String buffer) {
        ScoreResult score = new ScoreResult(); score.setUserId(UUID.randomUUID()); score.setMonth(month);
        score.setWeakestComponent(weakest); score.setDataCompleteness(new BigDecimal("0.65"));
        score.setComponentScores(new ComponentScoresDto(new BigDecimal("54"), new BigDecimal("70"), new BigDecimal("50"), null, null));
        Map<String, ScoreExplanation.Component> evidence = new LinkedHashMap<>();
        score.getComponentScores().toMap().forEach((key, value) -> evidence.put(key,
                new ScoreExplanation.Component(value == null ? "UNAVAILABLE" : "AVAILABLE", value, BigDecimal.ZERO,
                        BigDecimal.ZERO, 2, "repayment".equals(key) ? "SELF_DECLARED" : "CALCULATED", "BUFFER_ONLY",
                        key.equals("cashflow") ? List.of(new ScoreExplanation.Driver("cashBufferMonths", buffer, "months")) : List.of(), null, null)));
        score.setExplanation(new ScoreExplanation(2, null, null, null, evidence, List.of()));
        return score;
    }
}
