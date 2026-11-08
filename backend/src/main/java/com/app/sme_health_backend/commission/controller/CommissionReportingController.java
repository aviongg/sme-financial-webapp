package com.app.sme_health_backend.commission.controller;

import com.app.sme_health_backend.commission.dto.CommissionAnalyticsOverviewResponse;
import com.app.sme_health_backend.commission.dto.CommissionAuditEventResponse;
import com.app.sme_health_backend.commission.dto.CommissionPeriodTrendResponse;
import com.app.sme_health_backend.commission.dto.SalespersonPerformanceResponse;
import com.app.sme_health_backend.commission.dto.SalespersonStatementResponse;
import com.app.sme_health_backend.commission.service.CommissionReportingService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/finance/commissions")
public class CommissionReportingController {

    private final CommissionReportingService reportingService;
    private final BusinessAuthorizationService authorizationService;

    public CommissionReportingController(
            CommissionReportingService reportingService,
            BusinessAuthorizationService authorizationService
    ) {
        this.reportingService = reportingService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/analytics/overview")
    public ResponseEntity<CommissionAnalyticsOverviewResponse> getOverview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) UUID salespersonId,
            HttpServletRequest httpRequest
    ) {
        BusinessAccessContext context = authorizationService.requirePermission(
                httpRequest, BusinessPermission.COMMISSION_READ
        );
        enforceBusinessWideAnalyticsAccess(context);

        CommissionAnalyticsOverviewResponse response = reportingService.getOverview(
                context, startDate, endDate, salespersonId
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/analytics/salespersons")
    public ResponseEntity<List<SalespersonPerformanceResponse>> getSalespersonBreakdown(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            HttpServletRequest httpRequest
    ) {
        BusinessAccessContext context = authorizationService.requirePermission(
                httpRequest, BusinessPermission.COMMISSION_READ
        );
        enforceBusinessWideAnalyticsAccess(context);

        List<SalespersonPerformanceResponse> response = reportingService.getSalespersonBreakdown(
                context, startDate, endDate
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/analytics/periods")
    public ResponseEntity<List<CommissionPeriodTrendResponse>> getPeriodTrends(
            @RequestParam(defaultValue = "6") int months,
            HttpServletRequest httpRequest
    ) {
        BusinessAccessContext context = authorizationService.requirePermission(
                httpRequest, BusinessPermission.COMMISSION_READ
        );
        enforceBusinessWideAnalyticsAccess(context);

        List<CommissionPeriodTrendResponse> response = reportingService.getPeriodTrends(context, months);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/analytics/audit-ledger")
    public ResponseEntity<List<CommissionAuditEventResponse>> getAuditLedger(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) UUID salespersonId,
            @RequestParam(required = false) UUID counterpartyId,
            HttpServletRequest httpRequest
    ) {
        BusinessAccessContext context = authorizationService.requirePermission(
                httpRequest, BusinessPermission.COMMISSION_READ
        );
        enforceBusinessWideAnalyticsAccess(context);

        List<CommissionAuditEventResponse> response = reportingService.getAuditLedger(
                context, startDate, endDate, salespersonId, counterpartyId
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/statements/{salespersonId}")
    public ResponseEntity<SalespersonStatementResponse> getSalespersonStatement(
            @PathVariable UUID salespersonId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            HttpServletRequest httpRequest
    ) {
        BusinessAccessContext context = authorizationService.requirePermission(
                httpRequest, BusinessPermission.COMMISSION_READ
        );

        SalespersonStatementResponse response = reportingService.getSalespersonStatement(
                context, salespersonId, startDate, endDate
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/statements/{salespersonId}/export")
    public ResponseEntity<byte[]> exportSalespersonStatementCsv(
            @PathVariable UUID salespersonId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            HttpServletRequest httpRequest
    ) {
        BusinessAccessContext context = authorizationService.requirePermission(
                httpRequest, BusinessPermission.COMMISSION_READ
        );

        byte[] csv = reportingService.exportStatementCsv(context, salespersonId, startDate, endDate);
        String filename = "commission-statement-" + salespersonId + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv);
    }

    @GetMapping("/my-statement")
    public ResponseEntity<SalespersonStatementResponse> getMyStatement(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            HttpServletRequest httpRequest
    ) {
        BusinessAccessContext context = authorizationService.requirePermission(
                httpRequest, BusinessPermission.COMMISSION_READ
        );

        SalespersonStatementResponse response = reportingService.getSalespersonStatement(
                context, null, startDate, endDate
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-statement/export")
    public ResponseEntity<byte[]> exportMyStatementCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            HttpServletRequest httpRequest
    ) {
        BusinessAccessContext context = authorizationService.requirePermission(
                httpRequest, BusinessPermission.COMMISSION_READ
        );

        byte[] csv = reportingService.exportStatementCsv(context, null, startDate, endDate);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"my-commission-statement.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv);
    }

    private void enforceBusinessWideAnalyticsAccess(BusinessAccessContext context) {
        if (context.role() == MembershipRole.SALESPERSON) {
            throw new AccessDeniedException("Salespeople are not authorized to access business-wide commission analytics");
        }
    }
}
