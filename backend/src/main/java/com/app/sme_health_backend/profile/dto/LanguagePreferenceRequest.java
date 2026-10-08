package com.app.sme_health_backend.profile.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record LanguagePreferenceRequest(
        @NotNull @Pattern(regexp = "en|ur", message = "Language must be en or ur")
        String languagePreference
) {}
