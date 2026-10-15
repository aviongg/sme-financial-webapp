package com.app.sme_health_backend.accounting.repository;

import com.app.sme_health_backend.accounting.model.MovementDirection;

import java.math.BigDecimal;
import java.util.UUID;

public interface AccountMovementSum {
    UUID getAccountId();
    MovementDirection getMovementDirection();
    BigDecimal getTotalAmount();
}
