package com.app.sme_health_backend.projectattribution.controller;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.projectattribution.dto.*;
import com.app.sme_health_backend.projectattribution.exception.AttributionNotFoundException;
import com.app.sme_health_backend.projectattribution.exception.AttributionValidationException;
import com.app.sme_health_backend.projectattribution.service.ProjectFinancialAttributionService;
import com.app.sme_health_backend.projectattribution.service.ProjectFinancialCostingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/finance/projects/{projectId}")
public class ProjectFinancialAttributionController {

    private final ProjectFinancialAttributionService attributionService;
    private final ProjectFinancialCostingService costingService;
    private final BusinessAuthorizationService authService;

    public ProjectFinancialAttributionController(
            ProjectFinancialAttributionService attributionService,
            ProjectFinancialCostingService costingService,
            BusinessAuthorizationService authService
    ) {
        this.attributionService = attributionService;
        this.costingService = costingService;
        this.authService = authService;
    }

    @GetMapping("/eligible-sources")
    public ResponseEntity<List<EligibleSourceItem>> getEligibleSources(
            @PathVariable("projectId") UUID projectId,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_DATA_READ
        );
        List<EligibleSourceItem> response = attributionService.getEligibleSources(context.businessId(), projectId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/attributions")
    public ResponseEntity<AttributionResponse> createAttribution(
            @PathVariable("projectId") UUID projectId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody AttributionCreateRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.RECORD_CREATE_UPDATE
        );
        AttributionResponse response = attributionService.createAttribution(
                context.businessId(),
                projectId,
                requestDto,
                context.userId(),
                idempotencyKey
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/attributions")
    public ResponseEntity<List<AttributionResponse>> listAttributions(
            @PathVariable("projectId") UUID projectId,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_DATA_READ
        );
        List<AttributionResponse> response = attributionService.listAttributions(context.businessId(), projectId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/attributions/{attributionId}")
    public ResponseEntity<AttributionResponse> getAttributionDetail(
            @PathVariable("projectId") UUID projectId,
            @PathVariable("attributionId") UUID attributionId,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_DATA_READ
        );
        AttributionResponse response = attributionService.getAttributionDetail(
                context.businessId(), projectId, attributionId
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/attributions/{attributionId}/reverse")
    public ResponseEntity<AttributionResponse> reverseAttribution(
            @PathVariable("projectId") UUID projectId,
            @PathVariable("attributionId") UUID attributionId,
            @Valid @RequestBody AttributionReverseRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.RECORD_CREATE_UPDATE
        );
        AttributionResponse response = attributionService.reverseAttribution(
                context.businessId(), projectId, attributionId, requestDto, context.userId()
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/costing-summary")
    public ResponseEntity<ProjectCostingSummaryResponse> getCostingSummary(
            @PathVariable("projectId") UUID projectId,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_DATA_READ
        );
        ProjectCostingSummaryResponse response = costingService.getCostingSummary(
                context.businessId(), projectId
        );
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(AttributionValidationException.class)
    public ResponseEntity<Map<String, String>> handleValidation(AttributionValidationException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "ATTRIBUTION_VALIDATION_ERROR", "message", e.getMessage()));
    }

    @ExceptionHandler(AttributionNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(AttributionNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "ATTRIBUTION_NOT_FOUND", "message", e.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrity(DataIntegrityViolationException e) {
        String msg = e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : e.getMessage();
        return ResponseEntity.badRequest().body(Map.of("error", "DATA_INTEGRITY_VIOLATION", "message", msg != null ? msg : "Data integrity violation"));
    }
}
