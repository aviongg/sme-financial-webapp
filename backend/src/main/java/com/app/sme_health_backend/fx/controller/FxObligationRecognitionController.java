package com.app.sme_health_backend.fx.controller;

import com.app.sme_health_backend.fx.dto.FxRecognitionEvidenceRequest;
import com.app.sme_health_backend.fx.dto.FxRecognitionEvidenceResponse;
import com.app.sme_health_backend.fx.service.FxSettlementService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/finance/fx/obligations")
public class FxObligationRecognitionController {

    private final FxSettlementService settlementService;
    private final BusinessAuthorizationService authService;

    public FxObligationRecognitionController(
            FxSettlementService settlementService,
            BusinessAuthorizationService authService
    ) {
        this.settlementService = settlementService;
        this.authService = authService;
    }

    @PostMapping("/{obligationId}/recognition")
    public ResponseEntity<FxRecognitionEvidenceResponse> recordRecognition(
            @PathVariable("obligationId") UUID obligationId,
            @RequestBody FxRecognitionEvidenceRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.FINANCIAL_TRANSACTION_POST);
        FxRecognitionEvidenceResponse response = settlementService.recordRecognitionEvidence(context, obligationId, requestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{obligationId}/recognition")
    public ResponseEntity<FxRecognitionEvidenceResponse> getRecognition(
            @PathVariable("obligationId") UUID obligationId,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        FxRecognitionEvidenceResponse response = settlementService.getRecognitionEvidence(context, obligationId);
        return ResponseEntity.ok(response);
    }
}

