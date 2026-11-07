package com.app.sme_health_backend.funding.dto;

import java.time.LocalDate;

public record DailyScenarioDifference(
        LocalDate date,
        String baselineClosingPosition,
        String alternativeClosingPosition,
        String positionDifference,
        String baselineDeficit,
        String alternativeDeficit,
        String deficitDifference
) {}
