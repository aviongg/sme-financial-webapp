package com.app.sme_health_backend.records.projection.controller;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.records.projection.dto.MonthlyLedgerProjectionResponse;
import com.app.sme_health_backend.records.projection.dto.MonthlyShadowComparisonResponse;
import com.app.sme_health_backend.records.projection.service.MonthlyLedgerProjectionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing deterministic read-only monthly ledger projection and shadow comparison endpoints.
 */
@RestController
@RequestMapping("/api/records/monthly")
public class MonthlyLedgerProjectionController {

    private final MonthlyLedgerProjectionService projectionService;
    private final BusinessAuthorizationService authService;

    public MonthlyLedgerProjectionController(
            MonthlyLedgerProjectionService projectionService,
            BusinessAuthorizationService authService
    ) {
        this.projectionService = projectionService;
        this.authService = authService;
    }

    @GetMapping("/projection")
    public ResponseEntity<MonthlyLedgerProjectionResponse> getMonthlyLedgerProjection(
            @RequestParam(name = "month") String month,
            @RequestParam(name = "asOfDate", required = false) String asOfDate,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_DATA_READ
        );
        return ResponseEntity.ok(projectionService.projectMonthlyLedger(context, month, asOfDate));
    }

    @GetMapping("/comparison")
    public ResponseEntity<MonthlyShadowComparisonResponse> getMonthlyShadowComparison(
            @RequestParam(name = "month") String month,
            @RequestParam(name = "asOfDate", required = false) String asOfDate,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_DATA_READ
        );
        return ResponseEntity.ok(projectionService.compareWithMonthlyRecord(context, month, asOfDate));
    }
}
