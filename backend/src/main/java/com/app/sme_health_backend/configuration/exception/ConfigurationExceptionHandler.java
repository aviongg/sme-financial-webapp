package com.app.sme_health_backend.configuration.exception;

import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
@Order(-10)
public class ConfigurationExceptionHandler {
    @ExceptionHandler(ConfigurationException.class)
    public ResponseEntity<Map<String, Object>> configuration(ConfigurationException error) {
        return ResponseEntity.status(error.status()).body(Map.of("timestamp", Instant.now().toString(),
                "status", error.status(), "error", error.code(), "message", error.getMessage()));
    }
}
