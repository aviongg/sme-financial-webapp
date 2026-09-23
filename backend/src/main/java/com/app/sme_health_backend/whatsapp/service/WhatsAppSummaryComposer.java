package com.app.sme_health_backend.whatsapp.service;

import com.app.sme_health_backend.i18n.TranslationService;
import com.app.sme_health_backend.insight.entity.Insight;
import com.app.sme_health_backend.profile.entity.BusinessProfile;
import com.app.sme_health_backend.recommendation.entity.Recommendation;
import com.app.sme_health_backend.scoring.entity.ScoreResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

@Service
public class WhatsAppSummaryComposer {

    public static final String TEMPLATE_NAME = "financial_health_weekly_summary_v1";

    private final TranslationService translationService;
    private final String portalUrl;

    public WhatsAppSummaryComposer(
            TranslationService translationService,
            @Value("${app.whatsapp.portal-url:http://localhost:3000}") String portalUrl
    ) {
        this.translationService = translationService;
        this.portalUrl = (portalUrl != null && !portalUrl.isBlank()) ? portalUrl : "http://localhost:3000";
    }

    public record ComposedSummary(
            String messageText,
            String sourceFingerprint,
            String templateName,
            String targetMonth,
            String language
    ) {}

    public ComposedSummary compose(
            BusinessProfile profile,
            ScoreResult scoreResult,
            Insight insight,
            Recommendation recommendation
    ) {
        if (scoreResult == null) {
            throw new IllegalArgumentException("ScoreResult is required to compose summary");
        }

        String language = profile != null ? profile.getLanguagePreference() : "en";
        String resolvedLanguage = translationService.resolveLanguage(language);

        String businessName = (profile != null && profile.getBusinessType() != null && !profile.getBusinessType().isBlank())
                ? profile.getBusinessType()
                : "Valued Business";

        // Translate band using existing TranslationService
        String bandKey = scoreResult.getBand() != null
                ? "band." + scoreResult.getBand().trim().toLowerCase().replace(' ', '_')
                : "band.unknown";
        if (!translationService.hasKey(resolvedLanguage, bandKey)) {
            bandKey = "band.unknown";
        }
        String translatedBand = translationService.translate(resolvedLanguage, bandKey);

        // Translate weakest component using existing TranslationService
        String componentKey = scoreResult.getWeakestComponent() != null
                ? "component." + scoreResult.getWeakestComponent().trim().toLowerCase()
                : "component.weakest_component";
        if (!translationService.hasKey(resolvedLanguage, componentKey)) {
            componentKey = "component.weakest_component";
        }
        String translatedComponent = translationService.translate(resolvedLanguage, componentKey);

        String header = translationService.translate(
                resolvedLanguage,
                "whatsapp.summary.header",
                Map.of("month", scoreResult.getMonth())
        );

        String businessLine = translationService.translate(
                resolvedLanguage,
                "whatsapp.summary.business",
                Map.of("businessName", businessName)
        );

        int scoreValue = scoreResult.getCompositeScore() != null ? scoreResult.getCompositeScore().intValue() : 0;
        String scoreLine = translationService.translate(
                resolvedLanguage,
                "whatsapp.summary.score",
                Map.of("score", scoreValue, "band", translatedBand)
        );

        String weakestLine = translationService.translate(
                resolvedLanguage,
                "whatsapp.summary.weakest",
                Map.of("component", translatedComponent)
        );

        String insightText = (insight != null && insight.getText() != null) ? insight.getText() : "";
        String insightLine = translationService.translate(
                resolvedLanguage,
                "whatsapp.summary.insight",
                Map.of("insight", insightText)
        );

        String actionText = (recommendation != null && recommendation.getText() != null) ? recommendation.getText() : "";
        String actionLine = translationService.translate(
                resolvedLanguage,
                "whatsapp.summary.action",
                Map.of("action", actionText)
        );

        String footer = translationService.translate(
                resolvedLanguage,
                "whatsapp.summary.footer",
                Map.of("portalUrl", portalUrl)
        );

        String messageText = String.join("\n\n",
                header,
                businessLine,
                scoreLine,
                weakestLine,
                insightLine,
                actionLine,
                footer
        );

        String fingerprint = computeSourceFingerprint(
                scoreResult,
                insight,
                recommendation,
                resolvedLanguage,
                TEMPLATE_NAME
        );

        return new ComposedSummary(
                messageText,
                fingerprint,
                TEMPLATE_NAME,
                scoreResult.getMonth(),
                resolvedLanguage
        );
    }

    public static String computeSourceFingerprint(
            ScoreResult scoreResult,
            Insight insight,
            Recommendation recommendation,
            String language,
            String templateName
    ) {
        String raw = String.format("%s:%s:%s:%s:%s:%s:%s:%s",
                scoreResult != null ? scoreResult.getId() : "null-score",
                scoreResult != null ? scoreResult.getMonth() : "null-month",
                scoreResult != null ? scoreResult.getCompositeScore() : "0",
                scoreResult != null ? scoreResult.getBand() : "none",
                insight != null && insight.getSourceVersion() != null ? insight.getSourceVersion() : "no-insight",
                recommendation != null && recommendation.getSourceVersion() != null ? recommendation.getSourceVersion() : "no-rec",
                language != null ? language : "en",
                templateName != null ? templateName : "default"
        );

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
