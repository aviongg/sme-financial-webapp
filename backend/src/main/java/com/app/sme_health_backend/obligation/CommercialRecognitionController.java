package com.app.sme_health_backend.obligation;

import com.app.sme_health_backend.commercial.model.CommercialDocumentKind;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.obligation.dto.CommercialEligibleDocumentResponse;
import com.app.sme_health_backend.obligation.dto.CommercialRecognitionRequest;
import com.app.sme_health_backend.obligation.dto.CommercialRecognitionResponse;
import com.app.sme_health_backend.obligation.dto.CommercialRecognitionStatusResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/finance/obligations")
public class CommercialRecognitionController {

    private final CommercialRecognitionService recognitionService;
    private final BusinessAuthorizationService authService;

    public CommercialRecognitionController(
            CommercialRecognitionService recognitionService,
            BusinessAuthorizationService authService
    ) {
        this.recognitionService = recognitionService;
        this.authService = authService;
    }

    @PostMapping("/recognize-commercial-document")
    public ResponseEntity<CommercialRecognitionResponse> recognizeCommercialDocument(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @Valid @RequestBody CommercialRecognitionRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_POST
        );
        CommercialRecognitionResponse response = recognitionService.recognizeCommercialDocument(
                context, idempotencyKeyHeader, requestDto, request
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/commercial-recognition-status/{documentId}")
    public ResponseEntity<CommercialRecognitionStatusResponse> getRecognitionStatus(
            @PathVariable("documentId") UUID documentId,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_DATA_READ
        );
        CommercialRecognitionStatusResponse response = recognitionService.getRecognitionStatus(
                context, documentId
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/commercial-recognition/eligible-documents")
    public ResponseEntity<List<CommercialEligibleDocumentResponse>> getEligibleDocuments(
            @RequestParam(value = "kind", required = false) CommercialDocumentKind kind,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_DATA_READ
        );
        List<CommercialEligibleDocumentResponse> response = recognitionService.listEligibleDocuments(
                context, kind
        );
        return ResponseEntity.ok(response);
    }
}

