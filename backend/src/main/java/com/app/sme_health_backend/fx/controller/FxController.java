package com.app.sme_health_backend.fx.controller;

import com.app.sme_health_backend.fx.dto.CurrencyInfoResponse;
import com.app.sme_health_backend.fx.dto.FxPreviewRequest;
import com.app.sme_health_backend.fx.dto.FxPreviewResponse;
import com.app.sme_health_backend.fx.exception.FxValidationException;
import com.app.sme_health_backend.fx.service.CurrencyMetadataService;
import com.app.sme_health_backend.fx.service.FxCalculationService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for multicurrency metadata and foreign exchange conversion previews.
 * <p>
 * Strictly read-only simulation endpoints. Does not post transactions or mutate financial state.
 */
@RestController
@RequestMapping("/api/finance/fx")
public class FxController {

    private final FxCalculationService calculationService;
    private final CurrencyMetadataService currencyMetadataService;
    private final BusinessAuthorizationService authorizationService;

    public FxController(
            FxCalculationService calculationService,
            CurrencyMetadataService currencyMetadataService,
            BusinessAuthorizationService authorizationService
    ) {
        this.calculationService = calculationService;
        this.currencyMetadataService = currencyMetadataService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/currencies")
    public ResponseEntity<List<CurrencyInfoResponse>> getSupportedCurrencies(HttpServletRequest request) {
        authorizationService.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        List<CurrencyInfoResponse> currencies = currencyMetadataService.getAllCurrencies()
                .stream()
                .map(CurrencyInfoResponse::from)
                .toList();
        return ResponseEntity.ok(currencies);
    }

    @PostMapping("/preview")
    public ResponseEntity<FxPreviewResponse> previewConversion(
            @RequestBody FxPreviewRequest previewRequest,
            HttpServletRequest request
    ) {
        authorizationService.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        FxPreviewResponse response = calculationService.previewConversion(previewRequest);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(FxValidationException.class)
    public ResponseEntity<Map<String, Object>> handleFxValidationException(FxValidationException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Validation Failed");
        body.put("message", ex.getMessage());
        body.put("errors", ex.getErrors());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}

