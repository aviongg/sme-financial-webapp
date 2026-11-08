package com.app.sme_health_backend.fx.controller;

import com.app.sme_health_backend.fx.dto.*;
import com.app.sme_health_backend.fx.exception.FxValidationException;
import com.app.sme_health_backend.fx.service.FxSettlementService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/finance/fx/settlements")
public class FxSettlementController {

    private final FxSettlementService settlementService;
    private final BusinessAuthorizationService authService;

    public FxSettlementController(
            FxSettlementService settlementService,
            BusinessAuthorizationService authService
    ) {
        this.settlementService = settlementService;
        this.authService = authService;
    }

    @PostMapping("/preview")
    public ResponseEntity<FxSettlementPreviewResponse> previewSettlement(
            @RequestBody FxSettlementPreviewRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        FxSettlementPreviewResponse response = settlementService.previewSettlement(context, requestDto);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<FxSettlementResponse> postSettlement(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody FxSettlementPostRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.FINANCIAL_TRANSACTION_POST);
        FxSettlementResponse response = settlementService.postSettlement(context, idempotencyKey, requestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<FxSettlementResponse>> listSettlements(
            @RequestParam(name = "obligationId", required = false) UUID obligationId,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        List<FxSettlementResponse> responses = settlementService.listSettlements(context, obligationId);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FxSettlementResponse> getSettlement(
            @PathVariable("id") UUID id,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        FxSettlementResponse response = settlementService.getSettlement(context, id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/reverse")
    public ResponseEntity<FxSettlementResponse> reverseSettlement(
            @PathVariable("id") UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) FxSettlementReverseRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.FINANCIAL_TRANSACTION_POST);
        FxSettlementResponse response = settlementService.reverseSettlement(
                context,
                idempotencyKey,
                id,
                requestDto != null ? requestDto : new FxSettlementReverseRequest("FX settlement reversal")
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/reconciliation")
    public ResponseEntity<FxSettlementReconciliationResponse> getSettlementReconciliation(
            @PathVariable("id") UUID id,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        FxSettlementReconciliationResponse response = settlementService.getSettlementReconciliation(context, id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/reconciliation")
    public ResponseEntity<List<FxSettlementReconciliationResponse>> listSettlementReconciliations(
            @RequestParam(name = "obligationId", required = false) UUID obligationId,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        List<FxSettlementReconciliationResponse> responses = settlementService.listSettlementReconciliations(context, obligationId);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/summary")
    public ResponseEntity<FxPeriodSummaryResponse> getPeriodSummary(
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        FxPeriodSummaryResponse response = settlementService.getPeriodSummary(context, startDate, endDate);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/obligations/{obligationId}/recognition")
    public ResponseEntity<FxRecognitionEvidenceResponse> recordObligationRecognition(
            @PathVariable("obligationId") UUID obligationId,
            @RequestBody FxRecognitionEvidenceRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.FINANCIAL_TRANSACTION_POST);
        FxRecognitionEvidenceResponse response = settlementService.recordRecognitionEvidence(context, obligationId, requestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/obligations/{obligationId}/recognition")
    public ResponseEntity<FxRecognitionEvidenceResponse> getObligationRecognition(
            @PathVariable("obligationId") UUID obligationId,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        FxRecognitionEvidenceResponse response = settlementService.getRecognitionEvidence(context, obligationId);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(FxValidationException.class)
    public ResponseEntity<Map<String, Object>> handleFxValidationException(FxValidationException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Validation Failed");
        body.put("message", ex.getMessage());
        body.put("errors", ex.getErrors());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
