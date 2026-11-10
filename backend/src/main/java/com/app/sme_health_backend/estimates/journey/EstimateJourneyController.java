package com.app.sme_health_backend.estimates.journey;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** F06 journeys: new-project conversion, quotation draft invoice, estimate-vs-actual feedback. */
@RestController
@RequestMapping("/api/estimates/{estimateId}")
public class EstimateJourneyController {
    private final BusinessAuthorizationService authorization;
    private final EstimateJourneyService journeys;

    public EstimateJourneyController(BusinessAuthorizationService authorization, EstimateJourneyService journeys) {
        this.authorization = authorization;
        this.journeys = journeys;
    }

    public record NewProjectBody(String code, String name, String kind) { }
    public record QuotationBody(String customerName) { }

    @PostMapping("/versions/{versionId}/convert-to-new-project")
    public ResponseEntity<?> newProject(@PathVariable("estimateId") UUID estimateId, @PathVariable("versionId") UUID versionId,
                                        @RequestBody NewProjectBody body, HttpServletRequest request) {
        BusinessAccessContext ctx = authorization.requirePermission(request, BusinessPermission.RECORD_CREATE_UPDATE);
        return ResponseEntity.status(HttpStatus.CREATED).header("Cache-Control", "no-store")
                .body(journeys.convertToNewProject(ctx, estimateId, versionId, body.code(), body.name(), body.kind()));
    }

    @PostMapping("/versions/{versionId}/quotation-invoice")
    public ResponseEntity<?> quotation(@PathVariable("estimateId") UUID estimateId, @PathVariable("versionId") UUID versionId,
                                       @RequestBody(required = false) QuotationBody body, HttpServletRequest request) {
        BusinessAccessContext ctx = authorization.requirePermission(request, BusinessPermission.RECORD_CREATE_UPDATE);
        return ResponseEntity.status(HttpStatus.CREATED).header("Cache-Control", "no-store")
                .body(journeys.draftInvoice(ctx, estimateId, versionId, body == null ? null : body.customerName()));
    }

    @GetMapping("/feedback")
    public ResponseEntity<?> feedback(@PathVariable("estimateId") UUID estimateId, HttpServletRequest request) {
        BusinessAccessContext ctx = authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(journeys.feedback(ctx, estimateId));
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(com.app.sme_health_backend.estimates.service.ConversionPrecisionException.class)
    public ResponseEntity<?> unrepresentable(com.app.sme_health_backend.estimates.service.ConversionPrecisionException e) {
        return ResponseEntity.status(e.status()).header("Cache-Control", "no-store").body(java.util.Map.of(
                "status", e.status(), "error", e.code(), "message", e.getMessage(), "estimateValid", true, "details", e.issues()));
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(com.app.sme_health_backend.estimates.service.EstimateValidationException.class)
    public ResponseEntity<?> invalid(com.app.sme_health_backend.estimates.service.EstimateValidationException e) {
        return ResponseEntity.badRequest().header("Cache-Control", "no-store").body(java.util.Map.of(
                "status", 400, "error", "estimate_invalid", "message", e.getMessage(), "errors", e.errors()));
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(com.app.sme_health_backend.estimates.service.EstimateException.class)
    public ResponseEntity<?> domain(com.app.sme_health_backend.estimates.service.EstimateException e) {
        return ResponseEntity.status(e.status()).header("Cache-Control", "no-store").body(java.util.Map.of(
                "status", e.status(), "error", e.code(), "message", e.getMessage()));
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(com.app.sme_health_backend.projects.service.ProjectException.class)
    public ResponseEntity<?> project(com.app.sme_health_backend.projects.service.ProjectException e) {
        return ResponseEntity.status(e.status()).header("Cache-Control", "no-store").body(java.util.Map.of(
                "status", e.status(), "error", e.code(), "message", e.getMessage()));
    }
}
