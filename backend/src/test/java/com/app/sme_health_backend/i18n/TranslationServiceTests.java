package com.app.sme_health_backend.i18n;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TranslationServiceTests {

    @Test
    void shouldLoadEnglishResource() {
        TranslationService translationService =
                new TranslationService(new ObjectMapper());

        String text = translationService.translate(
                "en",
                "insight.focus_weakest_component",
                Map.of("component", "liquidity")
        );

        assertEquals(
                "Focus first on liquidity, which is currently your weakest financial area.",
                text
        );
    }

    @Test
    void shouldLoadUrduResource() {
        TranslationService translationService =
                new TranslationService(new ObjectMapper());

        String text = translationService.translate(
                "ur",
                "insight.focus_weakest_component",
                Map.of("component", "لیکویڈیٹی")
        );

        assertTrue(text.contains("لیکویڈیٹی"));
        assertFalse(text.isBlank());
    }

    @Test
    void shouldKeepEnglishAndUrduKeysMatched() {
        TranslationService translationService =
                new TranslationService(new ObjectMapper());

        assertEquals(
                translationService.keys("en"),
                translationService.keys("ur")
        );
    }

    @Test
    void shouldFallbackToEnglishForUnsupportedLanguage() {
        TranslationService translationService =
                new TranslationService(new ObjectMapper());

        String text = translationService.translate(
                "fr",
                "insight.data_quality.complete"
        );

        assertEquals(
                translationService.translate(
                        "en",
                        "insight.data_quality.complete"
                ),
                text
        );
    }

    @Test
    void shouldFailFastWhenSelectedLanguageKeyIsMissing() {
        TranslationService translationService = new TranslationService(
                Map.of(
                        "en",
                        Map.of("example.key", "English text"),
                        "ur",
                        Map.of()
                ),
                false
        );

        assertThrows(
                MissingResourceException.class,
                () -> translationService.translate("ur", "example.key")
        );
    }

    @Test
    void shouldRejectMissingEnglishKey() {
        TranslationService translationService =
                new TranslationService(new ObjectMapper());

        assertThrows(
                MissingResourceException.class,
                () -> translationService.translate("en", "missing.key")
        );
    }

    @Test
    void shouldRejectMismatchedResourceKeys() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new TranslationService(Map.of(
                        "en",
                        Map.of("one", "One"),
                        "ur",
                        Map.of("two", "Two")
                ))
        );

        assertTrue(exception.getMessage().contains("Translation keys do not match"));
    }

    @Test
    void shouldResolveBlankLanguageToEnglish() {
        TranslationService translationService =
                new TranslationService(new ObjectMapper());

        Set<String> englishKeys = translationService.keys("en");

        assertEquals(englishKeys, translationService.keys("   "));
    }

    @Test
    void shouldNormalizeUppercaseAndWhitespaceAndDefaultMissingLanguage() {
        TranslationService service = new TranslationService(new ObjectMapper());
        assertEquals("ur", service.resolveLanguage(" UR "));
        assertEquals("en", service.resolveLanguage(" EN "));
        assertEquals("en", service.resolveLanguage(null));
    }

    @Test
    void shouldRejectTranslationPlaceholderMismatchAtStartup() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> new TranslationService(Map.of(
                        "en", Map.of("example", "Score {score}"),
                        "ur", Map.of("example", "Score {value}")
                )));
        assertTrue(exception.getMessage().contains("placeholders do not match"));
    }

    @Test
    void shouldAllowReorderedAndRepeatedPlaceholders() {
        TranslationService service = new TranslationService(Map.of(
                "en", Map.of("example", "{component}: {score}"),
                "ur", Map.of("example", "{score} / {component} / {score}")
        ));
        assertEquals("52 / cash flow / 52", service.translate("ur", "example",
                Map.of("component", "cash flow", "score", 52)));
    }

    @Test
    void shouldRejectMissingOrNullParametersInsteadOfLeakingPlaceholders() {
        TranslationService service = new TranslationService(new ObjectMapper());
        assertThrows(IllegalArgumentException.class,
                () -> service.translate("en", "insight.focus_weakest_component"));
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("component", null);
        assertThrows(IllegalArgumentException.class,
                () -> service.translate("en", "insight.focus_weakest_component", parameters));
    }

    @Test
    void shouldKeepParameterSpecialCharactersLiteral() {
        TranslationService service = new TranslationService(new ObjectMapper());
        String text = service.translate("en", "insight.focus_weakest_component",
                Map.of("component", "$100\\cash"));
        assertTrue(text.contains("$100\\cash"));
    }

    @Test
    void shouldRejectBlankTranslationText() {
        assertThrows(IllegalStateException.class, () -> new TranslationService(Map.of(
                "en", Map.of("example", "English"),
                "ur", Map.of("example", " ")
        )));
    }
}
