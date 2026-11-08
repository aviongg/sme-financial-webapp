package com.app.sme_health_backend.fx.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record FxTransferPreviewRequest(
        UUID sourceAccountId,
        UUID destinationAccountId,
        BigDecimal sourceAmount,
        BigDecimal exchangeRate,
        BigDecimal feeAmount,
        String feePlacement
) {}
