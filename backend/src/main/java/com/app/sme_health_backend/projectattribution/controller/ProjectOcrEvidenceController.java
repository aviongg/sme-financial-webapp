package com.app.sme_health_backend.projectattribution.controller;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.projectattribution.dto.*;
import com.app.sme_health_backend.projectattribution.exception.AttributionNotFoundException;
import com.app.sme_health_backend.projectattribution.exception.AttributionValidationException;
import com.app.sme_health_backend.projectattribution.service.ProjectOcrEvidenceService;
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
public class ProjectOcrEvidenceController {

    private final ProjectOcrEvidenceService ocrEvidenceService;
    private final BusinessAuthorizationService authService;

    public ProjectOcrEvidenceController(
            ProjectOcrEvidenceService ocrEvidenceService,
            BusinessAuthorizationService authService
    ) {
        this.ocrEvidenceService = ocrEvidenceService;
        this.authService = authService;
    }

    // =========================================================================
    // OCR Candidates Listing
    // =========================================================================

    @GetMapping("/ocr-candidates")
    public ResponseEntity<List<OcrEvidenceCandidateResponse>> listEligibleCandidates(
            @PathVariable("projectId") UUID projectId,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_DATA_READ
        );
        List<OcrEvidenceCandidateResponse> response = ocrEvidenceService.listEligibleOcrCandidates(
                context.businessId(), projectId
        );
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // OCR Evidence Listing & Details
    // =========================================================================

    @GetMapping("/ocr-evidence")
    public ResponseEntity<List<ProjectOcrEvidenceItemResponse>> listProjectOcrEvidence(
            @PathVariable("projectId") UUID projectId,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_DATA_READ
        );
        List<ProjectOcrEvidenceItemResponse> response = ocrEvidenceService.listProjectOcrEvidence(
                context.businessId(), projectId
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/ocr-evidence/{linkId}")
    public ResponseEntity<ProjectOcrEvidenceItemResponse> getOcrEvidenceDetail(
            @PathVariable("projectId") UUID projectId,
            @PathVariable("linkId") UUID linkId,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_DATA_READ
        );
        ProjectOcrEvidenceItemResponse response = ocrEvidenceService.getOcrEvidenceDetail(
                context.businessId(), projectId, linkId
        );
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // Link Evidence to Project
    // =========================================================================

    @PostMapping("/ocr-evidence")
    public ResponseEntity<ProjectOcrEvidenceItemResponse> linkOcrEvidence(
            @PathVariable("projectId") UUID projectId,
            @Valid @RequestBody OcrEvidenceLinkRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.RECORD_CREATE_UPDATE
        );
        ProjectOcrEvidenceItemResponse response = ocrEvidenceService.linkOcrEvidenceToProject(
                context.businessId(), projectId, requestDto, context.userId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // =========================================================================
    // Confirm Match & Attribute
    // =========================================================================

    @PostMapping("/ocr-evidence/{linkId}/confirm-match")
    public ResponseEntity<ProjectOcrEvidenceItemResponse> confirmMatch(
            @PathVariable("projectId") UUID projectId,
            @PathVariable("linkId") UUID linkId,
            @RequestBody(required = false) OcrMatchConfirmationRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.RECORD_CREATE_UPDATE
        );
        OcrMatchConfirmationRequest effectiveDto = requestDto != null ? requestDto : new OcrMatchConfirmationRequest(null, null, null);
        ProjectOcrEvidenceItemResponse response = ocrEvidenceService.confirmMatchAndAttribute(
                context.businessId(), projectId, linkId, effectiveDto, context.userId()
        );
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // Invalidate Evidence Link
    // =========================================================================

    @PostMapping("/ocr-evidence/{linkId}/invalidate")
    public ResponseEntity<ProjectOcrEvidenceItemResponse> invalidateEvidenceLink(
            @PathVariable("projectId") UUID projectId,
            @PathVariable("linkId") UUID linkId,
            @RequestBody(required = false) OcrEvidenceInvalidateRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.RECORD_CREATE_UPDATE
        );
        String reason = (requestDto != null) ? requestDto.reason() : "Invalidated by user";
        ProjectOcrEvidenceItemResponse response = ocrEvidenceService.invalidateOcrEvidenceLink(
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
    public ResponseEntity<Map<String, String>> handleDataIntegrity(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Data integrity violation: " + ex.getMostSpecificCause().getMessage()));
    }
}
