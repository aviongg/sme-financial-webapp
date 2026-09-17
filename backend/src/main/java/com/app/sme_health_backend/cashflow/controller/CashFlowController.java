package com.app.sme_health_backend.cashflow.controller;

import com.app.sme_health_backend.cashflow.dto.CashFlowChartPointResponse;
import com.app.sme_health_backend.cashflow.dto.CashFlowProjectionResponse;
import com.app.sme_health_backend.cashflow.service.CashFlowService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cashflow")
public class CashFlowController {

    private final CashFlowService cashFlowService;

    public CashFlowController(CashFlowService cashFlowService) {
        this.cashFlowService = cashFlowService;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<List<CashFlowChartPointResponse>> getCashFlowHistory(
            @PathVariable String userId
    ) {
        UUID parsedUserId;
        try {
            parsedUserId = UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid user ID format: " + userId);
        }

        List<CashFlowChartPointResponse> response =
                cashFlowService.getCashFlowHistory(parsedUserId);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{userId}/projection")
    public ResponseEntity<CashFlowProjectionResponse> getTrendProjection(
            @PathVariable String userId
    ) {
        UUID parsedUserId;
        try {
            parsedUserId = UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid user ID format: " + userId);
        }

        CashFlowProjectionResponse response =
                cashFlowService.getTrendProjection(parsedUserId);

        return ResponseEntity.ok(response);
    }
}
