package com.app.sme_health_backend.obligation;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.obligation.dto.PaymentAllocateRequest;
import com.app.sme_health_backend.obligation.dto.PaymentAllocateResponse;
import com.app.sme_health_backend.obligation.dto.PaymentDetailResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/finance/payments")
public class PaymentController {

    private final PaymentAllocationService paymentAllocationService;
    private final BusinessAuthorizationService authService;

    public PaymentController(
            PaymentAllocationService paymentAllocationService,
            BusinessAuthorizationService authService
    ) {
        this.paymentAllocationService = paymentAllocationService;
        this.authService = authService;
    }

    @PostMapping("/allocate")
    public ResponseEntity<PaymentAllocateResponse> allocatePayment(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) PaymentAllocateRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_POST
        );
        PaymentAllocateResponse response = paymentAllocationService.allocatePayment(context, idempotencyKey, requestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentDetailResponse> getPayment(
            @PathVariable("id") UUID id,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_READ
        );
        PaymentDetailResponse response = paymentAllocationService.getPayment(context, id);
        return ResponseEntity.ok(response);
    }
}
