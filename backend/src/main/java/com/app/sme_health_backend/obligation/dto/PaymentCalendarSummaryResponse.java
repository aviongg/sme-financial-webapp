package com.app.sme_health_backend.obligation.dto;

public record PaymentCalendarSummaryResponse(
        String totalExpectedReceivable,
        String totalExpectedPayable,
        String overdueReceivable,
        String overduePayable,
        String upcomingReceivable,
        String upcomingPayable
) {
}
