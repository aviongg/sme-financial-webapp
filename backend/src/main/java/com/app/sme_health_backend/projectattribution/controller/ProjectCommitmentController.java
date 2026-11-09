package com.app.sme_health_backend.projectattribution.controller;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.projectattribution.dto.*;
import com.app.sme_health_backend.projectattribution.exception.AttributionNotFoundException;
import com.app.sme_health_backend.projectattribution.exception.AttributionValidationException;
import com.app.sme_health_backend.projectattribution.service.ProjectCommitmentService;
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
public class ProjectCommitmentController {

    private final ProjectCommitmentService commitmentService;
    private final BusinessAuthorizationService authService;

    public ProjectCommitmentController(
            ProjectCommitmentService commitmentService,
            BusinessAuthorizationService authService
    ) {
        this.commitmentService = commitmentService;
        this.authService = authService;
    }

    // =========================================================================
    // Summary & Financial Overview
    // =========================================================================

    @GetMapping("/commitments")
    public ResponseEntity<ProjectCommitmentsSummaryResponse> getCommitmentsSummary(
            @PathVariable("projectId") UUID projectId,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_DATA_READ
        );
        ProjectCommitmentsSummaryResponse response = commitmentService.getCommitmentsSummary(
                context.businessId(), projectId
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/financial-overview")
    public ResponseEntity<ProjectCommitmentsSummaryResponse> getFinancialOverview(
            @PathVariable("projectId") UUID projectId,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_DATA_READ
        );
        ProjectCommitmentsSummaryResponse response = commitmentService.getCommitmentsSummary(
                context.businessId(), projectId
        );
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // Commercial Document Links
    // =========================================================================

    @GetMapping("/commercial-documents")
    public ResponseEntity<List<CommercialDocumentLinkResponse>> listCommercialDocuments(
            @PathVariable("projectId") UUID projectId,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_DATA_READ
        );
        List<CommercialDocumentLinkResponse> response = commitmentService.listCommercialDocuments(
                context.businessId(), projectId
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping({"/commercial-document-links", "/document-links"})
    public ResponseEntity<CommercialDocumentLinkResponse> linkCommercialDocument(
            @PathVariable("projectId") UUID projectId,
            @Valid @RequestBody CommercialDocumentLinkRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.RECORD_CREATE_UPDATE
        );
        CommercialDocumentLinkResponse response = commitmentService.linkCommercialDocument(
                context.businessId(), projectId, requestDto, context.userId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/commercial-document-links/{linkId}/unlink")
    public ResponseEntity<CommercialDocumentLinkResponse> unlinkCommercialDocument(
            @PathVariable("projectId") UUID projectId,
            @PathVariable("linkId") UUID linkId,
            @RequestBody(required = false) CommercialDocumentUnlinkRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.RECORD_CREATE_UPDATE
        );
        String reason = (requestDto != null) ? requestDto.reason() : null;
        CommercialDocumentLinkResponse response = commitmentService.unlinkCommercialDocument(
                context.businessId(), projectId, linkId, reason, context.userId()
        );
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // Financial Obligation Links
    // =========================================================================

    @GetMapping("/obligations")
    public ResponseEntity<List<ProjectObligationItemResponse>> listProjectObligations(
            @PathVariable("projectId") UUID projectId,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_DATA_READ
        );
        List<ProjectObligationItemResponse> response = commitmentService.listProjectObligations(
                context.businessId(), projectId
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/obligation-links")
    public ResponseEntity<ProjectObligationItemResponse> linkFinancialObligation(
            @PathVariable("projectId") UUID projectId,
            @Valid @RequestBody FinancialObligationLinkRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.RECORD_CREATE_UPDATE
        );
        ProjectObligationItemResponse response = commitmentService.linkFinancialObligation(
                context.businessId(), projectId, requestDto, context.userId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/obligation-links/{linkId}/unlink")
    public ResponseEntity<ProjectObligationItemResponse> unlinkFinancialObligation(
            @PathVariable("projectId") UUID projectId,
            @PathVariable("linkId") UUID linkId,
            @RequestBody(required = false) FinancialObligationUnlinkRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.RECORD_CREATE_UPDATE
        );
        String reason = (requestDto != null) ? requestDto.reason() : null;
        ProjectObligationItemResponse response = commitmentService.unlinkFinancialObligation(
                context.businessId(), projectId, linkId, reason, context.userId()
        );
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // Exception Handlers
    // =========================================================================

    @ExceptionHandler(AttributionNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(AttributionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(AttributionValidationException.class)
    public ResponseEntity<Map<String, String>> handleValidation(AttributionValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleIntegrityViolation(DataIntegrityViolationException ex) {
        String msg = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", msg));
    }
}
