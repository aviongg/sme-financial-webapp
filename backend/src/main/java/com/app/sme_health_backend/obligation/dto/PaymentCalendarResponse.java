package com.app.sme_health_backend.obligation.dto;

import java.time.LocalDate;
import java.util.List;

public record PaymentCalendarResponse(
        LocalDate startDate,
        LocalDate endDate,
        PaymentCalendarSummaryResponse summary,
        List<PaymentCalendarItemResponse> items
) {
}
