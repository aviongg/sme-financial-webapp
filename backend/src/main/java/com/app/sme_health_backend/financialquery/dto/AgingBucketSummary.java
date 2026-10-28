package com.app.sme_health_backend.financialquery.dto;

public record AgingBucketSummary(
        String bucketCode,
        String bucketLabel,
        String amount,
        int count
) {}
