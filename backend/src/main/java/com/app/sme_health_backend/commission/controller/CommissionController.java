package com.app.sme_health_backend.commission.controller;

import com.app.sme_health_backend.commission.dto.*;
import com.app.sme_health_backend.commission.model.CommissionEarningStatus;
import com.app.sme_health_backend.commission.model.CommissionRuleStatus;
import com.app.sme_health_backend.commission.service.CommissionEarningService;
import com.app.sme_health_backend.commission.service.CommissionRuleService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/finance/commissions")
public class CommissionController {

    private final CommissionRuleService ruleService;
    private final CommissionEarningService earningService;
    private final com.app.sme_health_backend.commission.service.CommissionSettlementService settlementService;
    private final BusinessAuthorizationService authorizationService;

    public CommissionController(
            CommissionRuleService ruleService,
            CommissionEarningService earningService,
            com.app.sme_health_backend.commission.service.CommissionSettlementService settlementService,
            BusinessAuthorizationService authorizationService
    ) {
        this.ruleService = ruleService;
        this.earningService = earningService;
        this.settlementService = settlementService;
        this.authorizationService = authorizationService;
    }

    // --- RULES ---

    @PostMapping("/rules")
    public ResponseEntity<CommissionRuleResponse> createRule(
            @Valid @RequestBody CommissionRuleCreateRequest request,
            HttpServletRequest httpRequest
    ) {
        BusinessAccessContext context = authorizationService.requirePermission(
                httpRequest, BusinessPermission.COMMISSION_MANAGE
        );
        CommissionRuleResponse response = ruleService.createRule(context, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/rules")
    public ResponseEntity<List<CommissionRuleResponse>> listRules(
            @RequestParam(required = false) CommissionRuleStatus status,
            HttpServletRequest httpRequest
    ) {
        BusinessAccessContext context = authorizationService.requirePermission(
                httpRequest, BusinessPermission.COMMISSION_READ
        );
        List<CommissionRuleResponse> response = ruleService.listRules(context, status);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/rules/{id}")
    public ResponseEntity<CommissionRuleResponse> getRule(
            @PathVariable UUID id,
            HttpServletRequest httpRequest
    ) {
        BusinessAccessContext context = authorizationService.requirePermission(
                httpRequest, BusinessPermission.COMMISSION_READ
        );
        CommissionRuleResponse response = ruleService.getRule(context, id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/rules/{id}/status")
    public ResponseEntity<CommissionRuleResponse> updateRuleStatus(
            @PathVariable UUID id,
            @Valid @RequestBody CommissionRuleStatusUpdateRequest request,
            HttpServletRequest httpRequest
    ) {
        BusinessAccessContext context = authorizationService.requirePermission(
                httpRequest, BusinessPermission.COMMISSION_MANAGE
        );
        CommissionRuleResponse response = ruleService.updateRuleStatus(context, id, request.status());
        return ResponseEntity.ok(response);
    }

    // --- COLLECTIONS & CALCULATION ---

    @GetMapping("/eligible-collections")
    public ResponseEntity<List<EligibleCollectionResponse>> listEligibleCollections(
            HttpServletRequest httpRequest
    ) {
        BusinessAccessContext context = authorizationService.requirePermission(
                httpRequest, BusinessPermission.COMMISSION_READ
        );
        List<EligibleCollectionResponse> response = earningService.discoverEligibleCollections(context);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/preview")
    public ResponseEntity<CommissionCalculationPreviewResponse> previewCalculation(
            @Valid @RequestBody CommissionCalculationRequest request,
            HttpServletRequest httpRequest
    ) {
        BusinessAccessContext context = authorizationService.requirePermission(
                httpRequest, BusinessPermission.COMMISSION_READ
        );
        CommissionCalculationPreviewResponse response = earningService.previewCalculation(context, request);
        return ResponseEntity.ok(response);
    }

    // --- EARNINGS ---

    @PostMapping("/earnings")
    public ResponseEntity<CommissionEarningResponse> recordEarning(
            @Valid @RequestBody CommissionEarningCreateRequest request,
            HttpServletRequest httpRequest
    ) {
        BusinessAccessContext context = authorizationService.requirePermission(
                httpRequest, BusinessPermission.COMMISSION_MANAGE
        );
        CommissionEarningResponse response = earningService.recordEarning(context, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/earnings")
    public ResponseEntity<List<CommissionEarningResponse>> listEarnings(
            @RequestParam(required = false) UUID salespersonId,
            @RequestParam(required = false) CommissionEarningStatus status,
            HttpServletRequest httpRequest
    ) {
        BusinessAccessContext context = authorizationService.requirePermission(
                httpRequest, BusinessPermission.COMMISSION_READ
        );
        List<CommissionEarningResponse> response = earningService.listEarnings(context, salespersonId, status);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-earnings")
    public ResponseEntity<List<CommissionEarningResponse>> getMyEarnings(
            @RequestParam(required = false) CommissionEarningStatus status,
            HttpServletRequest httpRequest
    ) {
        BusinessAccessContext context = authorizationService.requirePermission(
                httpRequest, BusinessPermission.COMMISSION_READ
        );
        List<CommissionEarningResponse> response = earningService.listEarnings(context, null, status);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/earnings/{id}")
    public ResponseEntity<CommissionEarningResponse> getEarning(
            @PathVariable UUID id,
            HttpServletRequest httpRequest
    ) {
        BusinessAccessContext context = authorizationService.requirePermission(
                httpRequest, BusinessPermission.COMMISSION_READ
        );
        CommissionEarningResponse response = earningService.getEarning(context, id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/summary")
    public ResponseEntity<CommissionSummaryResponse> getSummary(
            @RequestParam(required = false) UUID salespersonId,
            HttpServletRequest httpRequest
    ) {
        BusinessAccessContext context = authorizationService.requirePermission(
                httpRequest, BusinessPermission.COMMISSION_READ
        );
        CommissionSummaryResponse response = earningService.getSummary(context, salespersonId);
        return ResponseEntity.ok(response);
    }

    // --- APPROVALS & SETTLEMENTS (F10C) ---

    @PostMapping("/earnings/{id}/approve")
    public ResponseEntity<CommissionEarningResponse> approveEarning(
            @PathVariable UUID id,
            @RequestBody(required = false) CommissionApprovalRequest request,
            HttpServletRequest httpRequest
    ) {
        BusinessAccessContext context = authorizationService.requirePermission(
                httpRequest, BusinessPermission.COMMISSION_MANAGE
        );
        CommissionEarningResponse response = settlementService.approveEarning(context, id, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/approvals/pending")
    public ResponseEntity<List<CommissionEarningResponse>> listPendingApprovals(
            HttpServletRequest httpRequest
    ) {
        BusinessAccessContext context = authorizationService.requirePermission(
                httpRequest, BusinessPermission.COMMISSION_MANAGE
        );
        List<CommissionEarningResponse> response = settlementService.listPendingApprovals(context);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/advances")
    public ResponseEntity<CommissionAdvanceResponse> createAdvance(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CommissionAdvanceCreateRequest request,
            HttpServletRequest httpRequest
    ) {
        BusinessAccessContext context = authorizationService.requirePermission(
                httpRequest, BusinessPermission.COMMISSION_MANAGE
        );
        CommissionAdvanceResponse response = settlementService.createAdvance(context, idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/advances")
    public ResponseEntity<List<CommissionAdvanceResponse>> listAdvances(
            @RequestParam(required = false) UUID salespersonId,
            HttpServletRequest httpRequest
    ) {
        BusinessAccessContext context = authorizationService.requirePermission(
                httpRequest, BusinessPermission.COMMISSION_READ
        );
        List<CommissionAdvanceResponse> response = settlementService.listAdvances(context, salespersonId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/earnings/{id}/apply-advance")
    public ResponseEntity<CommissionPayoutResponse> applyAdvance(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CommissionAdvanceApplyRequest request,
            HttpServletRequest httpRequest
    ) {
        BusinessAccessContext context = authorizationService.requirePermission(
                httpRequest, BusinessPermission.COMMISSION_MANAGE
        );
        CommissionPayoutResponse response = settlementService.applyAdvance(context, idempotencyKey, id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/earnings/{id}/payouts")
    public ResponseEntity<CommissionPayoutResponse> recordPayout(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CommissionPayoutCreateRequest request,
            HttpServletRequest httpRequest
    ) {
        BusinessAccessContext context = authorizationService.requirePermission(
                httpRequest, BusinessPermission.COMMISSION_MANAGE
        );
        CommissionPayoutResponse response = settlementService.recordPayout(context, idempotencyKey, id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/earnings/{id}/payouts")
    public ResponseEntity<List<CommissionPayoutResponse>> listPayoutsForEarning(
            @PathVariable UUID id,
            HttpServletRequest httpRequest
    ) {
        BusinessAccessContext context = authorizationService.requirePermission(
                httpRequest, BusinessPermission.COMMISSION_READ
        );
        List<CommissionPayoutResponse> response = settlementService.listPayoutsForEarning(context, id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/payouts")
    public ResponseEntity<List<CommissionPayoutResponse>> listPayouts(
            @RequestParam(required = false) UUID salespersonId,
            HttpServletRequest httpRequest
    ) {
        BusinessAccessContext context = authorizationService.requirePermission(
                httpRequest, BusinessPermission.COMMISSION_READ
        );
        List<CommissionPayoutResponse> response = settlementService.listPayouts(context, salespersonId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/payouts/{id}/reverse")
    public ResponseEntity<CommissionPayoutResponse> reversePayout(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CommissionPayoutReverseRequest request,
            HttpServletRequest httpRequest
    ) {
        BusinessAccessContext context = authorizationService.requirePermission(
                httpRequest, BusinessPermission.COMMISSION_MANAGE
        );
        CommissionPayoutResponse response = settlementService.reversePayout(context, idempotencyKey, id, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-settlements")
    public ResponseEntity<SalespersonSettlementSummaryResponse> getMySettlementSummary(
            HttpServletRequest httpRequest
    ) {
        BusinessAccessContext context = authorizationService.requirePermission(
                httpRequest, BusinessPermission.COMMISSION_READ
        );
        SalespersonSettlementSummaryResponse response = settlementService.getSalespersonSettlementSummary(context, null);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/salespersons/{id}/settlement-summary")
    public ResponseEntity<SalespersonSettlementSummaryResponse> getSalespersonSettlementSummary(
            @PathVariable UUID id,
            HttpServletRequest httpRequest
    ) {
        BusinessAccessContext context = authorizationService.requirePermission(
                httpRequest, BusinessPermission.COMMISSION_READ
        );
        SalespersonSettlementSummaryResponse response = settlementService.getSalespersonSettlementSummary(context, id);
        return ResponseEntity.ok(response);
    }
}
