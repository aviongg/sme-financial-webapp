package com.app.sme_health_backend.estimates.dto;

import java.util.UUID;

/** Only the user-selected existing F05 target and its expected version. Totals and lineage are server-derived. */
public record EstimateConversionRequest(UUID targetProjectId, Long expectedProjectVersion) { }
