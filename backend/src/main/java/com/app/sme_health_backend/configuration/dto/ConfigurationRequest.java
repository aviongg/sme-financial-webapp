package com.app.sme_health_backend.configuration.dto;

import com.app.sme_health_backend.configuration.model.OperatingProfile;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;

public record ConfigurationRequest(@NotNull OperatingProfile operatingProfile,
        @NotNull @Size(max = 3) Map<@NotBlank String, @NotNull Boolean> operatingAnswers,
        @NotNull @Size(min = 1, max = 20) List<@NotBlank String> requestedModules,
        @NotNull @PositiveOrZero Long version) {}
