package com.app.sme_health_backend.accounting.controller;

import com.app.sme_health_backend.accounting.dto.TransactionDetailResponse;
import com.app.sme_health_backend.accounting.dto.TransferPostRequest;
import com.app.sme_health_backend.accounting.service.TransferService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/finance/transfers")
public class TransferController {

    private final TransferService transferService;
    private final BusinessAuthorizationService authService;

    public TransferController(
            TransferService transferService,
            BusinessAuthorizationService authService
    ) {
        this.transferService = transferService;
        this.authService = authService;
    }

    @PostMapping
    public ResponseEntity<TransactionDetailResponse> postTransfer(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) TransferPostRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_POST
        );

        TransactionDetailResponse response = transferService.postTransfer(
                context, idempotencyKey, requestDto
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
