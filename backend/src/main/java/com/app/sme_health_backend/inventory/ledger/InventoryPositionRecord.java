package com.app.sme_health_backend.inventory.ledger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InventoryPositionRecord(
        UUID businessId,
        UUID itemId,
        BigDecimal quantity,
        BigDecimal value,
        long lastSequenceNo,
        LocalDate lastMovementDate,
        long version
) { }
