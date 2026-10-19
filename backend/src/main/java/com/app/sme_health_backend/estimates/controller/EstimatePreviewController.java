package com.app.sme_health_backend.estimates.controller;

import com.app.sme_health_backend.estimates.dto.PricingPreviewResponse;
import com.app.sme_health_backend.estimates.service.EstimateValidationException;
import com.app.sme_health_backend.estimates.service.PricingCalculator;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/estimates")
public class EstimatePreviewController {
    public static final int MAX_REQUEST_BYTES = 65_536;
    private final BusinessAuthorizationService authorization;
    private final PricingCalculator calculator;
    public EstimatePreviewController(BusinessAuthorizationService authorization, PricingCalculator calculator) {
        this.authorization = authorization; this.calculator = calculator;
    }
    @PostMapping(value = "/preview", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> preview(HttpServletRequest request) throws IOException {
        var context = authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        if (request.getContentLengthLong() > MAX_REQUEST_BYTES) return tooLarge();
        // Read at most limit + 1 even when Content-Length is missing or dishonest; never materialize an unbounded JSON tree.
        byte[] body = request.getInputStream().readNBytes(MAX_REQUEST_BYTES + 1);
        if (body.length > MAX_REQUEST_BYTES) return tooLarge();
        PricingPreviewResponse response = calculator.preview(context.businessId(), EstimateRequestReader.read(body));
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(response);
    }
    @ExceptionHandler(EstimateValidationException.class)
    public ResponseEntity<Map<String, Object>> invalid(EstimateValidationException exception) {
        return ResponseEntity.badRequest().header("Cache-Control", "no-store").body(Map.of(
                "status", 400, "error", "estimate_invalid", "message", exception.getMessage(), "errors", exception.errors()));
    }
    private static ResponseEntity<Map<String, Object>> tooLarge() {
        return ResponseEntity.status(413).header("Cache-Control", "no-store").body(Map.of(
                "status", 413, "error", "estimate_request_too_large", "message", "The planning estimate request is too large."));
    }
}
