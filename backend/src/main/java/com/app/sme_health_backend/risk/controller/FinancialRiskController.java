package com.app.sme_health_backend.risk.controller;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.risk.dto.FinancialRiskInsight;
import com.app.sme_health_backend.risk.dto.RiskAssessmentResponse;
import com.app.sme_health_backend.risk.dto.RiskSummary;
import com.app.sme_health_backend.risk.service.FinancialRiskAssessmentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/finance/risk")
public class FinancialRiskController {

    private final FinancialRiskAssessmentService riskService;
    private final BusinessAuthorizationService authorizationService;

    public FinancialRiskController(
            FinancialRiskAssessmentService riskService,
            BusinessAuthorizationService authorizationService
    ) {
        this.riskService = riskService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/assessment")
    public ResponseEntity<RiskAssessmentResponse> getRiskAssessment(
            @RequestParam(name = "asOfDate", required = false) String asOfDate,
            @RequestParam(name = "horizonDays", required = false, defaultValue = "30") Integer horizonDays,
            @RequestParam(name = "currency", required = false, defaultValue = "PKR") String currency,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "severity", required = false) String severity,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authorizationService.requirePermission(
                request, BusinessPermission.FINANCIAL_DATA_READ
        );
        RiskAssessmentResponse response = riskService.getAssessment(
                context, asOfDate, horizonDays, currency, category, severity
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/insights")
    public ResponseEntity<List<FinancialRiskInsight>> getRiskInsights(
            @RequestParam(name = "asOfDate", required = false) String asOfDate,
            @RequestParam(name = "horizonDays", required = false, defaultValue = "30") Integer horizonDays,
            @RequestParam(name = "currency", required = false, defaultValue = "PKR") String currency,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "severity", required = false) String severity,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authorizationService.requirePermission(
                request, BusinessPermission.FINANCIAL_DATA_READ
        );
        List<FinancialRiskInsight> insights = riskService.getInsights(
                context, asOfDate, horizonDays, currency, category, severity
        );
        return ResponseEntity.ok(insights);
    }

    @GetMapping("/summary")
    public ResponseEntity<RiskSummary> getRiskSummary(
            @RequestParam(name = "asOfDate", required = false) String asOfDate,
            @RequestParam(name = "horizonDays", required = false, defaultValue = "30") Integer horizonDays,
            @RequestParam(name = "currency", required = false, defaultValue = "PKR") String currency,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authorizationService.requirePermission(
                request, BusinessPermission.FINANCIAL_DATA_READ
        );
        RiskSummary summary = riskService.getSummary(
                context, asOfDate, horizonDays, currency
        );
        return ResponseEntity.ok(summary);
    }
}
