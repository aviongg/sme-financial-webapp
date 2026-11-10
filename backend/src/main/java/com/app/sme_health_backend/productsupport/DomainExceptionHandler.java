package com.app.sme_health_backend.productsupport;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/** Maps {@link DomainException} to a stable, non-cached JSON error body. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DomainExceptionHandler {
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<Map<String, Object>> handle(DomainException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", ex.status());
        body.put("error", ex.code());
        body.put("message", ex.getMessage());
        if (!ex.errors().isEmpty()) {
            body.put("errors", ex.errors());
        }
        return ResponseEntity.status(ex.status()).header("Cache-Control", "no-store").body(body);
    }
}
