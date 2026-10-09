package com.app.sme_health_backend.cashflow.controller;

import com.app.sme_health_backend.cashflow.dto.CashFlowChartPointResponse;
import com.app.sme_health_backend.cashflow.dto.CashFlowProjectionResponse;
import com.app.sme_health_backend.cashflow.service.CashFlowService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cashflow")
public class CashFlowController {

    private final CashFlowService cashFlowService;
    private final BusinessAuthorizationService authService;

    public CashFlowController(
            CashFlowService cashFlowService,
            BusinessAuthorizationService authService
    ) {
        this.cashFlowService = cashFlowService;
        this.authService = authService;
    }

    @GetMapping
    public ResponseEntity<List<CashFlowChartPointResponse>> getCashFlowHistory(HttpServletRequest request) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);

        List<CashFlowChartPointResponse> response =
                cashFlowService.getCashFlowHistory(context.businessId());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/projection")
    public ResponseEntity<CashFlowProjectionResponse> getTrendProjection(HttpServletRequest request) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);

        CashFlowProjectionResponse response =
                cashFlowService.getTrendProjection(context.businessId());

        return ResponseEntity.ok(response);
    }
}
