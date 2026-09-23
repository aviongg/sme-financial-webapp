package com.app.sme_health_backend.i18n;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TranslationService {

    public static final String DEFAULT_LANGUAGE = "en";
    public static final String URDU_LANGUAGE = "ur";

    private static final Set<String> SUPPORTED_LANGUAGES =
            Set.of(DEFAULT_LANGUAGE, URDU_LANGUAGE);
    private static final Pattern PLACEHOLDER_PATTERN =
            Pattern.compile("\\{([A-Za-z0-9_]+)}");

    private final Map<String, Map<String, String>> resources;

    @Autowired
    public TranslationService(ObjectMapper objectMapper) {
        this(loadResources(objectMapper));
    }

    public TranslationService(Map<String, Map<String, String>> resources) {
        this(resources, true);
    }

    public TranslationService(
            Map<String, Map<String, String>> resources,
            boolean validateMatchingKeys
    ) {
        this.resources = immutableCopy(resources);
        validateSupportedResources();

        if (validateMatchingKeys) {
            validateMatchingKeys();
        }
    }

    public String translate(String language, String key) {
        return translate(language, key, Map.of());
    }

    public String translate(
            String language,
            String key,
            Map<String, ?> parameters
    ) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Translation key is required");
        }

        String resolvedLang = resolveLanguage(language);
        String template = resolveTemplate(resolvedLang, key);

        return applyParameters(template, parameters == null
                ? Map.of()
                : parameters);
    }

    public String resolveLanguage(String language) {
        if (language == null || language.isBlank()) {
            return DEFAULT_LANGUAGE;
        }

        String normalizedLanguage = language.trim().toLowerCase(Locale.ROOT);

        if (!SUPPORTED_LANGUAGES.contains(normalizedLanguage)) {
            return DEFAULT_LANGUAGE;
        }

        return normalizedLanguage;
    }

    public boolean hasKey(String key) {
        return hasKey(DEFAULT_LANGUAGE, key);
    }

    public boolean hasKey(String language, String key) {
        if (key == null || key.isBlank()) {
            return false;
        }

        Map<String, String> selectedResource =
                resources.get(resolveLanguage(language));

        return selectedResource != null && selectedResource.containsKey(key);
    }

    public Set<String> keys(String language) {
        Map<String, String> selectedResource =
                resources.get(resolveLanguage(language));

        return selectedResource == null
                ? Set.of()
                : selectedResource.keySet();
    }

    private String resolveTemplate(String language, String key) {
        Map<String, String> selectedResource = resources.get(language);

        if (selectedResource != null && selectedResource.containsKey(key)) {
            return selectedResource.get(key);
        }

        // Fail fast: do not silently mask missing translations with English fallback
        throw new MissingResourceException(
                "Translation key not found for language '" + language + "': " + key,
                TranslationService.class.getName(),
                key
        );
    }

    private String applyParameters(
            String template,
            Map<String, ?> parameters
    ) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder translatedText = new StringBuilder();

        while (matcher.find()) {
            String parameterName = matcher.group(1);

            if (!parameters.containsKey(parameterName)
                    || parameters.get(parameterName) == null) {
                throw new IllegalArgumentException(
                        "Missing translation parameter: " + parameterName
                );
            }

            matcher.appendReplacement(
                    translatedText,
                    Matcher.quoteReplacement(
                            String.valueOf(parameters.get(parameterName))
                    )
            );
        }

        matcher.appendTail(translatedText);

        return translatedText.toString();
    }

    private void validateSupportedResources() {
        for (String supportedLanguage : SUPPORTED_LANGUAGES) {
            if (!resources.containsKey(supportedLanguage)) {
                throw new IllegalStateException(
                        "Missing translation resource for language: "
                                + supportedLanguage
                );
            }
            resources.get(supportedLanguage).forEach((key, value) -> {
                if (key == null || key.isBlank() || value == null || value.isBlank()) {
                    throw new IllegalStateException(
                            "Empty translation in language: " + supportedLanguage);
                }
            });
        }
    }

    private void validateMatchingKeys() {
        Set<String> defaultKeys = resources.get(DEFAULT_LANGUAGE).keySet();

        for (String language : SUPPORTED_LANGUAGES) {
            if (!resources.get(language).keySet().equals(defaultKeys)) {
                throw new IllegalStateException(
                        "Translation keys do not match for language: "
                                + language
                );
            }
            for (String key : defaultKeys) {
                if (!placeholders(resources.get(DEFAULT_LANGUAGE).get(key))
                        .equals(placeholders(resources.get(language).get(key)))) {
                    throw new IllegalStateException(
                            "Translation placeholders do not match for language: "
                                    + language + ", key: " + key);
                }
            }
        }
    }

    private Set<String> placeholders(String template) {
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private static Map<String, Map<String, String>> loadResources(
            ObjectMapper objectMapper
    ) {
        return Map.of(
                DEFAULT_LANGUAGE,
                loadResource(objectMapper, DEFAULT_LANGUAGE),
                URDU_LANGUAGE,
                loadResource(objectMapper, URDU_LANGUAGE)
        );
    }

    private static Map<String, String> loadResource(
            ObjectMapper objectMapper,
            String language
    ) {
        ClassPathResource resource =
                new ClassPathResource("i18n/" + language + ".json");

        try (InputStream inputStream = resource.getInputStream()) {
            return objectMapper.readValue(
                    inputStream,
                    new TypeReference<>() {
                    }
            );
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to load translation resource: " + language,
                    exception
            );
        }
    }

    private static Map<String, Map<String, String>> immutableCopy(
            Map<String, Map<String, String>> resources
    ) {
        Map<String, Map<String, String>> copy = new LinkedHashMap<>();

        resources.forEach((language, translations) ->
                copy.put(
                        language,
                        Collections.unmodifiableMap(
                                new LinkedHashMap<>(translations)
                        )
                )
        );

        return Collections.unmodifiableMap(copy);
    }
}
