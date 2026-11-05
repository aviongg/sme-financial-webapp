package com.app.sme_health_backend.reports.controller;

import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.reports.dto.BusinessFinancialReport;
import com.app.sme_health_backend.reports.dto.ReportRequest;
import com.app.sme_health_backend.reports.service.BusinessFinancialReportCsv;
import com.app.sme_health_backend.reports.service.BusinessFinancialReportService;
import com.app.sme_health_backend.reports.service.ReportValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

/**
 * F12B Phase A. Stateless read-only reporting under the existing session/CSRF/active-business architecture.
 * The tenant is always the authenticated active business; FINANCIAL_DATA_READ is the only permission required.
 */
@RestController
@RequestMapping("/api/reports")
public class BusinessFinancialReportController {
    public static final int MAX_REQUEST_BYTES = 2_048;
    private final BusinessAuthorizationService authorization;
    private final BusinessFinancialReportService service;

    public BusinessFinancialReportController(BusinessAuthorizationService authorization, BusinessFinancialReportService service) {
        this.authorization = authorization;
        this.service = service;
    }

    @PostMapping(value = "/business-financial-summary", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> preview(HttpServletRequest request) throws IOException {
        var context = authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        ResponseEntity<?> oversized = oversized(request);
        if (oversized != null) return oversized;
        BusinessFinancialReport report = service.generate(context.businessId(), read(request));
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(report);
    }

    @PostMapping(value = "/business-financial-summary.csv", consumes = "application/json")
    public ResponseEntity<?> export(HttpServletRequest request) throws IOException {
        var context = authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        ResponseEntity<?> oversized = oversized(request);
        if (oversized != null) return oversized;
        BusinessFinancialReport report = service.generate(context.businessId(), read(request));
        byte[] body = BusinessFinancialReportCsv.render(report);
        return ResponseEntity.ok()
                .header("Content-Type", BusinessFinancialReportCsv.CONTENT_TYPE)
                .header("Content-Disposition", "attachment; filename=\"" + BusinessFinancialReportCsv.fileName(report) + "\"")
                .header("Cache-Control", "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(body);
    }

    private ResponseEntity<?> oversized(HttpServletRequest request) {
        return request.getContentLengthLong() > MAX_REQUEST_BYTES ? tooLarge() : null;
    }

    /** Reads at most limit + 1 bytes even if Content-Length is missing or dishonest. */
    private ReportRequest read(HttpServletRequest request) throws IOException {
        byte[] body = request.getInputStream().readNBytes(MAX_REQUEST_BYTES + 1);
        if (body.length > MAX_REQUEST_BYTES) throw new RequestTooLarge();
        return ReportRequestReader.read(body);
    }

    private static final class RequestTooLarge extends RuntimeException { }

    @ExceptionHandler(RequestTooLarge.class)
    public ResponseEntity<Map<String, Object>> requestTooLarge() {
        return tooLarge();
    }

    @ExceptionHandler(ReportValidationException.class)
    public ResponseEntity<Map<String, Object>> invalid(ReportValidationException exception) {
        return ResponseEntity.badRequest().header("Cache-Control", "no-store").body(Map.of(
                "status", 400, "error", "report_invalid", "message", exception.getMessage(), "errors", exception.errors()));
    }

    private static ResponseEntity<Map<String, Object>> tooLarge() {
        return ResponseEntity.status(413).header("Cache-Control", "no-store").body(Map.of(
                "status", 413, "error", "report_request_too_large", "message", "The report request is too large."));
    }
}
