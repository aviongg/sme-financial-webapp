package com.app.sme_health_backend.forecast.controller;

import com.app.sme_health_backend.forecast.dto.DailyCashForecastResponse;
import com.app.sme_health_backend.forecast.dto.ForecastScenarioRequest;
import com.app.sme_health_backend.forecast.dto.ForecastScenarioResponse;
import com.app.sme_health_backend.forecast.service.DailyCashForecastService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/finance/forecast")
public class CashForecastController {

    private final DailyCashForecastService forecastService;
    private final BusinessAuthorizationService authorizationService;

    public CashForecastController(
            DailyCashForecastService forecastService,
            BusinessAuthorizationService authorizationService
    ) {
        this.forecastService = forecastService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/daily")
    public ResponseEntity<DailyCashForecastResponse> getDailyCashForecast(
            @RequestParam(name = "currency", required = false, defaultValue = "PKR") String currency,
            @RequestParam(name = "horizonDays", required = false, defaultValue = "30") Integer horizonDays,
            @RequestParam(name = "asOfDate", required = false) String asOfDate,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authorizationService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_READ
        );
        DailyCashForecastResponse response = forecastService.getDailyCashForecast(
                context, currency, horizonDays, asOfDate
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/scenario-preview")
    public ResponseEntity<ForecastScenarioResponse> previewScenario(
            @RequestBody ForecastScenarioRequest scenarioRequest,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authorizationService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_READ
        );
        ForecastScenarioResponse response = forecastService.previewScenario(
                context, scenarioRequest
        );
        return ResponseEntity.ok(response);
    }
}

