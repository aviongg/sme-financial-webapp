package com.app.sme_health_backend.insight.controller;

import com.app.sme_health_backend.insight.dto.InsightResponse;
import com.app.sme_health_backend.insight.service.InsightService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/insights")
public class InsightController {

    private final InsightService insightService;

    public InsightController(InsightService insightService) {
        this.insightService = insightService;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<List<InsightResponse>> getInsights(
            @PathVariable UUID userId
    ) {
        List<InsightResponse> responses =
                insightService.getInsights(userId)
                        .stream()
                        .map(InsightResponse::fromEntity)
                        .toList();

        return ResponseEntity.ok(responses);
    }
}
