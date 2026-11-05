package com.app.sme_health_backend.reports.dto;

/** Closed request: the business always comes from the authenticated active-business context. */
public record ReportRequest(String fromMonth, String toMonth, String language) { }
