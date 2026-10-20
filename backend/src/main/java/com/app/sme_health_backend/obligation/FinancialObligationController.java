package com.app.sme_health_backend.obligation;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.obligation.aging.ObligationAgingResult;
import com.app.sme_health_backend.obligation.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/finance/obligations")
public class FinancialObligationController {

    private final FinancialObligationService obligationService;
    private final PaymentPlanService paymentPlanService;
    private final BusinessAuthorizationService authService;

    public FinancialObligationController(
            FinancialObligationService obligationService,
            PaymentPlanService paymentPlanService,
            BusinessAuthorizationService authService
    ) {
        this.obligationService = obligationService;
        this.paymentPlanService = paymentPlanService;
        this.authService = authService;
    }

    @PostMapping
    public ResponseEntity<ObligationDetailResponse> createObligation(
            @RequestBody ObligationCreateRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_POST
        );
        ObligationDetailResponse response = obligationService.createObligation(context, requestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ObligationDetailResponse> updateObligation(
            @PathVariable("id") UUID id,
            @RequestBody ObligationUpdateRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_POST
        );
        ObligationDetailResponse response = obligationService.updateObligation(context, id, requestDto);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/void")
    public ResponseEntity<ObligationDetailResponse> voidObligation(
            @PathVariable("id") UUID id,
            @RequestBody ObligationVoidRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_POST
        );
        ObligationDetailResponse response = obligationService.voidObligation(context, id, requestDto);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/settlements")
    public ResponseEntity<ObligationSettlementResponse> recordSettlement(
            @PathVariable("id") UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody ObligationSettlementRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_POST
        );
        ObligationSettlementResponse response = obligationService.recordSettlement(context, id, idempotencyKey, requestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ObligationDetailResponse> getObligation(
            @PathVariable("id") UUID id,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_READ
        );
        ObligationDetailResponse response = obligationService.getObligation(context, id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<ObligationListResponse> listObligations(
            @RequestParam(name = "direction", required = false) String direction,
            @RequestParam(name = "counterpartyId", required = false) UUID counterpartyId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "settlementStatus", required = false) String settlementStatus,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_READ
        );
        int boundedPage = Math.max(0, page);
        int boundedSize = Math.max(1, Math.min(100, size));
        Pageable pageable = PageRequest.of(boundedPage, boundedSize, Sort.by(Sort.Direction.DESC, "issueDate", "createdAt"));

        ObligationListResponse response = obligationService.listObligations(
                context, direction, counterpartyId, status, settlementStatus, search, pageable
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/summary")
    public ResponseEntity<ObligationSummaryTotalsResponse> getTotalsSummary(
            @RequestParam(name = "asOfDate", required = false) String asOfDate,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_READ
        );
        ObligationSummaryTotalsResponse response = obligationService.getTotalsSummary(context, asOfDate);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/aging")
    public ResponseEntity<ObligationAgingResult> getAging(
            @RequestParam(name = "asOfDate", required = false) String asOfDate,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_READ
        );
        ObligationAgingResult response = obligationService.getAging(context, asOfDate);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/payment-plan")
    public ResponseEntity<PaymentPlanResponse> getPaymentPlan(
            @PathVariable("id") UUID id,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_READ
        );
        PaymentPlanResponse response = paymentPlanService.getPaymentPlan(context, id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/payment-plan")
    public ResponseEntity<PaymentPlanResponse> setPaymentPlan(
            @PathVariable("id") UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) PaymentPlanRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_POST
        );
        PaymentPlanResponse response = paymentPlanService.setPaymentPlan(context, idempotencyKey, id, requestDto);
        return ResponseEntity.ok(response);
    }
}
