package com.app.sme_health_backend.financialquery.controller;

import com.app.sme_health_backend.financialquery.FinancialQueryFacade;
import com.app.sme_health_backend.financialquery.dto.*;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/finance/query")
public class FinancialQueryController {

    private final FinancialQueryFacade financialQueryFacade;
    private final BusinessAuthorizationService authService;

    public FinancialQueryController(
            FinancialQueryFacade financialQueryFacade,
            BusinessAuthorizationService authService
    ) {
        this.financialQueryFacade = financialQueryFacade;
        this.authService = authService;
    }

    @GetMapping("/capabilities")
    public ResponseEntity<CapabilitiesResponse> getCapabilities(HttpServletRequest request) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_DATA_READ
        );
        return ResponseEntity.ok(financialQueryFacade.getCapabilities(context));
    }

    @GetMapping("/cash-position")
    public ResponseEntity<FinancialQueryEnvelope<CashPositionResult>> getCashPosition(
            @RequestParam(name = "asOfDate", required = false) String asOfDate,
            @RequestParam(name = "accountType", required = false) String accountType,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_ACCOUNT_READ
        );
        CashPositionQuery query = new CashPositionQuery(asOfDate, accountType);
        return ResponseEntity.ok(financialQueryFacade.getCashPosition(context, query));
    }

    @GetMapping("/overdue-receivables")
    public ResponseEntity<FinancialQueryEnvelope<OverdueReceivablesResult>> getOverdueReceivables(
            @RequestParam(name = "asOfDate", required = false) String asOfDate,
            @RequestParam(name = "limit", required = false) String limit,
            @RequestParam(name = "offset", required = false) String offset,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_READ
        );
        Integer parsedLimit = parseOptionalInt(limit, "limit");
        Integer parsedOffset = parseOptionalInt(offset, "offset");
        OverdueReceivablesQuery query = new OverdueReceivablesQuery(asOfDate, parsedLimit, parsedOffset);
        return ResponseEntity.ok(financialQueryFacade.getOverdueReceivables(context, query));
    }

    @GetMapping("/upcoming-obligations")
    public ResponseEntity<FinancialQueryEnvelope<UpcomingObligationsResult>> getUpcomingObligations(
            @RequestParam(name = "direction", required = false) String direction,
            @RequestParam(name = "fromDate", required = false) String fromDate,
            @RequestParam(name = "toDate", required = false) String toDate,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_READ
        );
        UpcomingObligationsQuery query = new UpcomingObligationsQuery(direction, fromDate, toDate);
        return ResponseEntity.ok(financialQueryFacade.getUpcomingObligations(context, query));
    }

    @GetMapping("/project-financials")
    public ResponseEntity<FinancialQueryEnvelope<Void>> getProjectFinancials(
            @RequestParam(name = "projectId", required = false) String projectId,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_DATA_READ
        );
        UUID parsedProjectId = parseOptionalUuid(projectId, "projectId");
        return ResponseEntity.ok(financialQueryFacade.getProjectFinancials(context, parsedProjectId));
    }

    @GetMapping("/project-funding-gap")
    public ResponseEntity<FinancialQueryEnvelope<Void>> getProjectFundingGap(
            @RequestParam(name = "projectId", required = false) String projectId,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_DATA_READ
        );
        UUID parsedProjectId = parseOptionalUuid(projectId, "projectId");
        return ResponseEntity.ok(financialQueryFacade.getProjectFundingGap(context, parsedProjectId));
    }

    private UUID parseOptionalUuid(String rawValue, String fieldName) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(rawValue.trim());
        } catch (IllegalArgumentException e) {
            throw new com.app.sme_health_backend.financialquery.exception.FinancialQueryValidationException(
                    fieldName, "Invalid UUID format"
            );
        }
    }

    private Integer parseOptionalInt(String rawValue, String fieldName) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(rawValue.trim());
        } catch (NumberFormatException e) {
            throw new com.app.sme_health_backend.financialquery.exception.FinancialQueryValidationException(
                    fieldName, "Invalid integer format"
            );
        }
    }

    @GetMapping("/margin-drivers")
    public ResponseEntity<FinancialQueryEnvelope<Void>> getMarginDrivers(
            @RequestParam(name = "query", required = false) String query,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_DATA_READ
        );
        return ResponseEntity.ok(financialQueryFacade.getMarginDrivers(context, query));
    }

    @GetMapping("/fx-settlement-impact")
    public ResponseEntity<FinancialQueryEnvelope<Void>> getFxSettlementImpact(
            @RequestParam(name = "query", required = false) String query,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_DATA_READ
        );
        return ResponseEntity.ok(financialQueryFacade.getFxSettlementImpact(context, query));
    }
}
