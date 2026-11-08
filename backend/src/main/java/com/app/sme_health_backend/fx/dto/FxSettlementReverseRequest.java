package com.app.sme_health_backend.fx.dto;

public record FxSettlementReverseRequest(
        String reason
) {
    public FxSettlementReverseRequest {
        if (reason == null || reason.isBlank()) {
            reason = "FX settlement reversal";
        }
    }
}

