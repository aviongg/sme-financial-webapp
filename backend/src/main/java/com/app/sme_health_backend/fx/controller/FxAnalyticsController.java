package com.app.sme_health_backend.fx.controller;

import com.app.sme_health_backend.fx.dto.*;
import com.app.sme_health_backend.fx.model.FxSettlementStatus;
import com.app.sme_health_backend.fx.model.RecognitionEvidenceStatus;
import com.app.sme_health_backend.fx.model.SettlementDirection;
import com.app.sme_health_backend.fx.service.FxAnalyticsService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/finance/fx/analytics")
public class FxAnalyticsController {

    private final FxAnalyticsService analyticsService;
    private final BusinessAuthorizationService authService;

    public FxAnalyticsController(
            FxAnalyticsService analyticsService,
            BusinessAuthorizationService authService
    ) {
        this.analyticsService = analyticsService;
        this.authService = authService;
    }

    @GetMapping("/overview")
    public ResponseEntity<FxAnalyticsOverviewResponse> getOverview(HttpServletRequest request) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        FxAnalyticsOverviewResponse response = analyticsService.getAnalyticsOverview(context);
        return ResponseEntity.ok(response);
    }

    @GetMapping({"/currencies", "/exposures"})
    public ResponseEntity<List<CurrencyExposureSummary>> getCurrencyExposures(HttpServletRequest request) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        List<CurrencyExposureSummary> response = analyticsService.getCurrencyExposures(context);
        return ResponseEntity.ok(response);
    }

    @GetMapping({"/periods", "/trends"})
    public ResponseEntity<List<FxPeriodTrendSummary>> getPeriodicTrends(HttpServletRequest request) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        List<FxPeriodTrendSummary> response = analyticsService.getPeriodicTrends(context);
        return ResponseEntity.ok(response);
    }

    @GetMapping({"/settlements", "/report"})
    public ResponseEntity<List<FxSettlementReportItem>> getSettlementsReport(
            @RequestParam(name = "currency", required = false) String currency,
            @RequestParam(name = "counterpartyId", required = false) UUID counterpartyId,
            @RequestParam(name = "direction", required = false) SettlementDirection direction,
            @RequestParam(name = "status", required = false) FxSettlementStatus status,
            @RequestParam(name = "evidenceStatus", required = false) RecognitionEvidenceStatus evidenceStatus,
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(name = "limit", required = false, defaultValue = "50") Integer limit,
            @RequestParam(name = "offset", required = false, defaultValue = "0") Integer offset,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        FxSettlementReportFilter filter = new FxSettlementReportFilter(
                currency, counterpartyId, direction, status, evidenceStatus, startDate, endDate, limit, offset
        );
        List<FxSettlementReportItem> items = analyticsService.getSettlementsReport(context, filter);
        return ResponseEntity.ok(items);
    }

    @GetMapping({"/export", "/report/csv"})
    public ResponseEntity<String> exportCsv(
            @RequestParam(name = "currency", required = false) String currency,
            @RequestParam(name = "counterpartyId", required = false) UUID counterpartyId,
            @RequestParam(name = "direction", required = false) SettlementDirection direction,
            @RequestParam(name = "status", required = false) FxSettlementStatus status,
            @RequestParam(name = "evidenceStatus", required = false) RecognitionEvidenceStatus evidenceStatus,
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        FxSettlementReportFilter filter = new FxSettlementReportFilter(
                currency, counterpartyId, direction, status, evidenceStatus, startDate, endDate, 500, 0
        );
        String csvContent = analyticsService.exportSettlementsCsv(context, filter);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDispositionFormData("attachment", "fx-settlements-statement.csv");

        return ResponseEntity.ok().headers(headers).body(csvContent);
    }
}
