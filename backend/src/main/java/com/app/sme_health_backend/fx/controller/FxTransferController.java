package com.app.sme_health_backend.fx.controller;

import com.app.sme_health_backend.fx.dto.*;
import com.app.sme_health_backend.fx.exception.FxValidationException;
import com.app.sme_health_backend.fx.service.FxTransferService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/finance/fx/transfers")
public class FxTransferController {

    private final FxTransferService fxTransferService;
    private final BusinessAuthorizationService authService;

    public FxTransferController(
            FxTransferService fxTransferService,
            BusinessAuthorizationService authService
    ) {
        this.fxTransferService = fxTransferService;
        this.authService = authService;
    }

    @PostMapping("/preview")
    public ResponseEntity<FxTransferPreviewResponse> previewTransfer(
            @RequestBody FxTransferPreviewRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_DATA_READ
        );
        FxTransferPreviewResponse response = fxTransferService.previewTransfer(context, requestDto);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<FxTransferResponse> postTransfer(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody FxTransferPostRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_POST
        );
        FxTransferResponse response = fxTransferService.postTransfer(context, idempotencyKey, requestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<FxTransferResponse>> listTransfers(
            @RequestParam(required = false) String status,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_DATA_READ
        );
        List<FxTransferResponse> response = fxTransferService.listTransfers(context, status);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FxTransferResponse> getTransfer(
            @PathVariable UUID id,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_DATA_READ
        );
        FxTransferResponse response = fxTransferService.getTransfer(context, id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/reverse")
    public ResponseEntity<FxTransferResponse> reverseTransfer(
            @PathVariable UUID id,
            @RequestBody(required = false) FxTransferReverseRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_POST
        );
        FxTransferResponse response = fxTransferService.reverseTransfer(context, id, requestDto);
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
