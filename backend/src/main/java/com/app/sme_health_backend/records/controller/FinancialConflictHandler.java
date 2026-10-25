package com.app.sme_health_backend.records.controller;

import com.app.sme_health_backend.records.service.FinancialConflictException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

@RestControllerAdvice(assignableTypes = {MonthlyRecordController.class, com.app.sme_health_backend.documents.controller.DocumentController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FinancialConflictHandler {
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadableRequest() {
        return ResponseEntity.badRequest().header("Cache-Control", "no-store").body(Map.of(
                "status", 400, "error", "invalid_request", "message", "Request fields or numeric values are invalid."));
    }
    @ExceptionHandler(FinancialConflictException.class)
    public ResponseEntity<Map<String, Object>> conflict(FinancialConflictException exception) {
        return ResponseEntity.status(409).header("Cache-Control", "no-store").body(Map.of(
                "status", 409, "error", exception.code(), "message", exception.getMessage(), "errors", exception.errors()));
    }
}