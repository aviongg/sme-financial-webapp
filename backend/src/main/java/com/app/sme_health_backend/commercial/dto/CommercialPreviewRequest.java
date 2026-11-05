package com.app.sme_health_backend.commercial.dto;

import java.util.List;

public record CommercialPreviewRequest(List<CommercialLineRequest> lines) { }
