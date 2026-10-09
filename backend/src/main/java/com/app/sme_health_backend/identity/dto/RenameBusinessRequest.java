package com.app.sme_health_backend.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameBusinessRequest(@NotBlank @Size(max = 120) String businessName) {}
