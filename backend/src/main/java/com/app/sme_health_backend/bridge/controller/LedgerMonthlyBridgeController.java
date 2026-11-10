package com.app.sme_health_backend.bridge.controller;

import com.app.sme_health_backend.bridge.dto.LedgerCutoverRequest;
import com.app.sme_health_backend.bridge.dto.LedgerCutoverResponse;
import com.app.sme_health_backend.bridge.dto.LedgerMonthlyProjectionResponse;
import com.app.sme_health_backend.bridge.service.LedgerMonthlyBridgeService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/finance/ledger-bridge")
public class LedgerMonthlyBridgeController {

    private final LedgerMonthlyBridgeService bridgeService;
    private final BusinessAuthorizationService authService;

    public LedgerMonthlyBridgeController(
            LedgerMonthlyBridgeService bridgeService,
            BusinessAuthorizationService authService
    ) {
        this.bridgeService = bridgeService;
        this.authService = authService;
    }

    @GetMapping("/monthly-projection")
    public ResponseEntity<LedgerMonthlyProjectionResponse> getMonthlyProjection(
            @RequestParam("yearMonth") String yearMonth,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_DATA_READ
        );
        LedgerMonthlyProjectionResponse response = bridgeService.getMonthlyProjection(context, yearMonth);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/apply-projection")
    public ResponseEntity<LedgerCutoverResponse> applyLedgerProjection(
            @Valid @RequestBody LedgerCutoverRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_POST
        );
        LedgerCutoverResponse response = bridgeService.applyLedgerProjection(context, requestDto, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

