package com.app.sme_health_backend.search.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SearchRequest(
        @Size(max = 200) String query,
        @Pattern(regexp = "(?i)(all|transaction|insight|recommendation|document|score)?") String type
) {}
