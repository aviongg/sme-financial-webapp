package com.app.sme_health_backend.configuration.controller;

import com.app.sme_health_backend.configuration.dto.*;
import com.app.sme_health_backend.configuration.service.BusinessConfigurationService;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/business-configuration")
public class BusinessConfigurationController {
    private final BusinessConfigurationService service;
    private final BusinessAuthorizationService authorization;
    public BusinessConfigurationController(BusinessConfigurationService service, BusinessAuthorizationService authorization) {
        this.service = service; this.authorization = authorization;
    }
    @GetMapping
    public ResponseEntity<ConfigurationResponse> get(HttpServletRequest request) {
        return response(service.get(authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ)));
    }
    @PutMapping
    public ResponseEntity<ConfigurationResponse> update(@Valid @RequestBody ConfigurationRequest input, HttpServletRequest request) {
        return response(service.update(authorization.requirePermission(request, BusinessPermission.BUSINESS_SETTINGS_MANAGE), input));
    }
    @PostMapping("/preview")
    public ResponseEntity<ConfigurationResponse> preview(@Valid @RequestBody ConfigurationRequest input, HttpServletRequest request) {
        return response(service.preview(authorization.requirePermission(request, BusinessPermission.BUSINESS_SETTINGS_MANAGE), input));
    }
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<java.util.Map<String, Object>> invalidBody() {
        return ResponseEntity.badRequest().body(java.util.Map.of("status", 400, "error", "invalid_configuration",
                "message", "Invalid configuration request."));
    }
    private ResponseEntity<ConfigurationResponse> response(ConfigurationResponse value) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(value);
    }
}
