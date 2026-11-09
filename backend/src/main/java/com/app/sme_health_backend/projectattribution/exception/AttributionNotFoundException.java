package com.app.sme_health_backend.projectattribution.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class AttributionNotFoundException extends RuntimeException {
    public AttributionNotFoundException(String message) {
        super(message);
    }
}
