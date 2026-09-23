package com.app.sme_health_backend.whatsapp.service;

import com.app.sme_health_backend.i18n.TranslationService;
import com.app.sme_health_backend.insight.entity.Insight;
import com.app.sme_health_backend.profile.entity.BusinessProfile;
import com.app.sme_health_backend.recommendation.entity.Recommendation;
import com.app.sme_health_backend.scoring.entity.ScoreResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WhatsAppSummaryComposerTest {

    private WhatsAppSummaryComposer composer;
    private TranslationService translationService;

    @BeforeEach
    void setUp() {
        translationService = new TranslationService(new ObjectMapper());
        composer = new WhatsAppSummaryComposer(translationService, "https://sme.app");
    }

    @Test
    void shouldComposeEnglishSummaryWithTranslatedBandAndComponent() {
        BusinessProfile profile = new BusinessProfile();
        profile.setUserId(UUID.randomUUID());
        profile.setBusinessType("Retail Mart");
        profile.setLanguagePreference("en");

        ScoreResult score = new ScoreResult();
        score.setId(UUID.randomUUID());
        score.setMonth("2026-09");
        score.setCompositeScore(new BigDecimal("78.50"));
        score.setBand("STRONG");
        score.setWeakestComponent("cashflow");

        Insight insight = new Insight();
        insight.setText("Your overall financial score is 78.5. Keep building on this progress.");
        insight.setSourceVersion("v1-ins");

        Recommendation rec = new Recommendation();
        rec.setText("Review cash inflows and outflows weekly to protect short-term cash flow.");
        rec.setSourceVersion("v1-rec");

        WhatsAppSummaryComposer.ComposedSummary summary = composer.compose(profile, score, insight, rec);

        assertNotNull(summary);
        assertEquals("financial_health_weekly_summary_v1", summary.templateName());
        assertEquals("2026-09", summary.targetMonth());
        assertEquals("en", summary.language());
        assertNotNull(summary.sourceFingerprint());
        assertEquals(64, summary.sourceFingerprint().length());

        String text = summary.messageText();
        assertTrue(text.contains("SME Financial Health Summary - 2026-09"));
        assertTrue(text.contains("Business: Retail Mart"));
        assertTrue(text.contains("Overall Score: 78/100 (Strong)"));
        assertTrue(text.contains("Area to Watch: cash flow"));
        assertTrue(text.contains("Key Insight:"));
        assertTrue(text.contains("Recommended Action:"));
        assertTrue(text.contains("https://sme.app"));

        assertNotNull(summary.templateParameters());
        assertEquals(8, summary.templateParameters().size());
        assertEquals("2026-09", summary.templateParameters().get(0));
        assertEquals("Retail Mart", summary.templateParameters().get(1));
        assertEquals("78", summary.templateParameters().get(2));
        assertEquals("Strong", summary.templateParameters().get(3));
        assertEquals("cash flow", summary.templateParameters().get(4));
        assertEquals("https://sme.app", summary.templateParameters().get(7));
    }

    @Test
    void shouldComposeUrduSummaryWithTranslatedBandAndComponent() {
        BusinessProfile profile = new BusinessProfile();
        profile.setUserId(UUID.randomUUID());
        profile.setBusinessType("احمد ٹریڈرز");
        profile.setLanguagePreference("ur");

        ScoreResult score = new ScoreResult();
        score.setId(UUID.randomUUID());
        score.setMonth("2026-09");
        score.setCompositeScore(new BigDecimal("65.00"));
        score.setBand("STABLE");
        score.setWeakestComponent("profitability");

        Insight insight = new Insight();
        insight.setText("آپ کا مجموعی مالی اسکور 65 ہے۔ اجزا کی نگرانی جاری رکھیں۔");
        insight.setSourceVersion("v1-ins-ur");

        Recommendation rec = new Recommendation();
        rec.setText("منافع بخشی بہتر بنانے کے لیے اخراجات کا جائزہ لیں۔");
        rec.setSourceVersion("v1-rec-ur");

        WhatsAppSummaryComposer.ComposedSummary summary = composer.compose(profile, score, insight, rec);

        assertNotNull(summary);
        assertEquals("ur", summary.language());
        String text = summary.messageText();
        assertTrue(text.contains("ایس ایم ای مالی صحت کا خلاصہ - 2026-09"));
        assertTrue(text.contains("احمد ٹریڈرز"));
        assertTrue(text.contains("مستحکم"));
        assertTrue(text.contains("منافع بخشی"));
    }

    @Test
    void shouldComputeDeterministicFingerprintAndChangeWhenSourceChanges() {
        ScoreResult score1 = new ScoreResult();
        score1.setId(UUID.randomUUID());
        score1.setMonth("2026-09");
        score1.setCompositeScore(new BigDecimal("70"));
        score1.setBand("STABLE");

        Insight insight1 = new Insight();
        insight1.setSourceVersion("v1");

        Recommendation rec1 = new Recommendation();
        rec1.setSourceVersion("v1");

        String fp1 = WhatsAppSummaryComposer.computeSourceFingerprint(score1, insight1, rec1, "en", "tpl");
        String fp2 = WhatsAppSummaryComposer.computeSourceFingerprint(score1, insight1, rec1, "en", "tpl");

        assertEquals(fp1, fp2);
        assertEquals(64, fp1.length());

        // Change language
        String fpLang = WhatsAppSummaryComposer.computeSourceFingerprint(score1, insight1, rec1, "ur", "tpl");
        assertNotEquals(fp1, fpLang);

        // Change advice version
        Insight insight2 = new Insight();
        insight2.setSourceVersion("v2");
        String fpAdvice = WhatsAppSummaryComposer.computeSourceFingerprint(score1, insight2, rec1, "en", "tpl");
        assertNotEquals(fp1, fpAdvice);
    }
}
