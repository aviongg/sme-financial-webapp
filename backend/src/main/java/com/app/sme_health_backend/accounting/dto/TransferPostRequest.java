package com.app.sme_health_backend.accounting.dto;

import java.util.UUID;

/**
 * Request payload for posting an internal funds transfer between two PKR accounts.
 */
public record TransferPostRequest(
        UUID sourceAccountId,
        UUID destinationAccountId,
        String amount,
        String effectiveDate,
        String description
) {}
