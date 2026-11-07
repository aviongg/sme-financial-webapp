package com.app.sme_health_backend.funding.controller;

import com.app.sme_health_backend.funding.dto.FundingScenarioCompareRequest;
import com.app.sme_health_backend.funding.dto.FundingScenarioCompareResponse;
import com.app.sme_health_backend.funding.exception.FundingScenarioValidationException;
import com.app.sme_health_backend.funding.service.FundingScenarioService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST controller exposing funding-gap analysis and what-if scenario comparisons.
 * <p>
 * Strictly read-only simulation endpoint. Does not post financial transactions or mutate database state.
 */
@RestController
@RequestMapping("/api/finance/funding")
public class FundingScenarioController {

    private final FundingScenarioService scenarioService;
    private final BusinessAuthorizationService authorizationService;

    public FundingScenarioController(
            FundingScenarioService scenarioService,
            BusinessAuthorizationService authorizationService
    ) {
        this.scenarioService = scenarioService;
        this.authorizationService = authorizationService;
    }

    @PostMapping("/scenarios/compare")
    public ResponseEntity<FundingScenarioCompareResponse> compareScenarios(
            @RequestBody FundingScenarioCompareRequest compareRequest,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authorizationService.requirePermission(
                request, BusinessPermission.FINANCIAL_DATA_READ
        );
        FundingScenarioCompareResponse response = scenarioService.compareScenarios(context, compareRequest);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(FundingScenarioValidationException.class)
    public ResponseEntity<Map<String, Object>> handleFundingScenarioValidationException(
            FundingScenarioValidationException ex
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Validation Failed");
        body.put("message", ex.getMessage());
        body.put("errors", ex.getErrors());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
