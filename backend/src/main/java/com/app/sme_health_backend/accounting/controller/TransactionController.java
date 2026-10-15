package com.app.sme_health_backend.accounting.controller;

import com.app.sme_health_backend.accounting.dto.TransactionDetailResponse;
import com.app.sme_health_backend.accounting.dto.TransactionListResponse;
import com.app.sme_health_backend.accounting.dto.TransactionPostRequest;
import com.app.sme_health_backend.accounting.dto.TransactionSummaryResponse;
import com.app.sme_health_backend.accounting.service.TransactionPostService;
import com.app.sme_health_backend.accounting.service.TransactionReadService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/finance/transactions")
public class TransactionController {

    private final TransactionPostService transactionPostService;
    private final TransactionReadService transactionReadService;
    private final BusinessAuthorizationService authService;

    public TransactionController(
            TransactionPostService transactionPostService,
            TransactionReadService transactionReadService,
            BusinessAuthorizationService authService
    ) {
        this.transactionPostService = transactionPostService;
        this.transactionReadService = transactionReadService;
        this.authService = authService;
    }

    @PostMapping
    public ResponseEntity<TransactionDetailResponse> postTransaction(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) TransactionPostRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_POST
        );

        TransactionDetailResponse response = transactionPostService.postTransaction(
                context, idempotencyKey, requestDto
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/summary")
    public ResponseEntity<TransactionSummaryResponse> getTransactionSummary(
            @RequestParam(name = "accountId", required = false) UUID accountId,
            @RequestParam(name = "eventType", required = false) String eventType,
            @RequestParam(name = "startDate", required = false) String startDate,
            @RequestParam(name = "endDate", required = false) String endDate,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_READ
        );

        TransactionSummaryResponse response = transactionReadService.getTransactionSummary(
                context.businessId(), accountId, eventType, startDate, endDate
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id:[0-9a-fA-F\\-]{36}}")
    public ResponseEntity<TransactionDetailResponse> getTransactionDetail(
            @PathVariable("id") UUID id,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_READ
        );

        TransactionDetailResponse response = transactionReadService.getTransactionDetail(
                context.businessId(), id
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<TransactionListResponse> listTransactions(
            @RequestParam(name = "accountId", required = false) UUID accountId,
            @RequestParam(name = "eventType", required = false) String eventType,
            @RequestParam(name = "startDate", required = false) String startDate,
            @RequestParam(name = "endDate", required = false) String endDate,
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "20") int size,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_READ
        );

        TransactionListResponse response = transactionReadService.listTransactions(
                context.businessId(), accountId, eventType, startDate, endDate, page, size
        );
        return ResponseEntity.ok(response);
    }
}
