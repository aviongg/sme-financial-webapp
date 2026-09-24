package com.app.sme_health_backend.insight.controller;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.insight.dto.InsightResponse;
import com.app.sme_health_backend.insight.service.InsightService;
import com.app.sme_health_backend.shared.dto.MonthQueryRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/insights")
public class InsightController {

    private final InsightService insightService;
    private final BusinessAuthorizationService authService;

    public InsightController(
            InsightService insightService,
            BusinessAuthorizationService authService
    ) {
        this.insightService = insightService;
        this.authService = authService;
    }

    @GetMapping
    public ResponseEntity<List<InsightResponse>> getLatestInsights(HttpServletRequest request) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);

        List<InsightResponse> responses = insightService.getInsights(context.businessId())
                .stream()
                .map(InsightResponse::fromEntity)
                .toList();

        return ResponseEntity.ok(responses);
    }

    @PostMapping("/query")
    public ResponseEntity<List<InsightResponse>> queryInsights(
            @Valid @RequestBody MonthQueryRequest queryRequest,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);

        List<InsightResponse> responses = insightService.getInsights(context.businessId(), queryRequest.month())
                .stream()
                .map(InsightResponse::fromEntity)
                .toList();

        return ResponseEntity.ok(responses);
    }
}
