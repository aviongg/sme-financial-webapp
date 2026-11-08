package com.app.sme_health_backend.fx.dto;

import java.util.UUID;

public record FxTransferPostRequest(
        UUID sourceAccountId,
        UUID destinationAccountId,
        String sourceAmount,
        String exchangeRate,
        String feeAmount,
        String feePlacement,
        String transferDate,
        String notes
) {}
