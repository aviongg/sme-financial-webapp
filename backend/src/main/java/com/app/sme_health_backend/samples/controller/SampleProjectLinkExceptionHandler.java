package com.app.sme_health_backend.samples.controller;

import com.app.sme_health_backend.projects.controller.ProjectController;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;
import java.util.Map;

/** Translate only the F09 context-link deletion constraint; F05 source remains unchanged. */
@RestControllerAdvice(assignableTypes = ProjectController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SampleProjectLinkExceptionHandler {
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> linkedProjectDeletion(
            DataIntegrityViolationException exception, HttpServletRequest request) {
        if ("DELETE".equals(request.getMethod()) && isSampleProjectLinkViolation(exception)) {
            return ResponseEntity.status(409).header("Cache-Control", "no-store").body(Map.of(
                    "status", 409,
                    "error", "project_delete_forbidden",
                    "message", "This project is linked to sample or production planning. Remove the context link from an open activity before deleting, or archive the project."));
        }
        // Preserve the application's existing handling of every unrelated database failure.
        throw exception;
    }

    private static boolean isSampleProjectLinkViolation(Throwable exception) {
        // PostgreSQL is a runtime-only dependency. JDBC SQLSTATE plus the exact
        // quoted constraint token identifies this FK without exposing SQL details.
        Throwable current = exception;
        for (int depth = 0; current != null && depth < 16; depth++) {
            if (current instanceof SQLException sql && "23503".equals(sql.getSQLState())
                    && sql.getMessage() != null && sql.getMessage().contains("\"fk_sample_activities_project\"")) {
                return true;
            }
            Throwable next = current.getCause();
            if (next == current) break;
            current = next;
        }
        return false;
    }
}
