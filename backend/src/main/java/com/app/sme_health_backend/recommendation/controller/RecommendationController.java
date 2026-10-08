package com.app.sme_health_backend.recommendation.controller;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.recommendation.dto.RecommendationResponse;
import com.app.sme_health_backend.recommendation.service.RecommendationService;
import com.app.sme_health_backend.shared.dto.MonthQueryRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final BusinessAuthorizationService authService;

    public RecommendationController(
            RecommendationService recommendationService,
            BusinessAuthorizationService authService
    ) {
        this.recommendationService = recommendationService;
        this.authService = authService;
    }

    @GetMapping
    public ResponseEntity<List<RecommendationResponse>> getLatestRecommendations(HttpServletRequest request) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);

        List<RecommendationResponse> responses = recommendationService.getRecommendations(context.businessId())
                .stream()
                .map(RecommendationResponse::fromEntity)
                .toList();

        return ResponseEntity.ok(responses);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<RecommendationResponse> updateStatus(
            @PathVariable java.util.UUID id,
            @Valid @RequestBody com.app.sme_health_backend.recommendation.dto.RecommendationStatusRequest body,
            HttpServletRequest request) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.RECORD_CREATE_UPDATE);
        return ResponseEntity.ok(RecommendationResponse.fromEntity(
                recommendationService.updateStatus(context.businessId(), id, body.status(), context.userId())));
    }

    @PostMapping("/query")
    public ResponseEntity<List<RecommendationResponse>> queryRecommendations(
            @Valid @RequestBody MonthQueryRequest queryRequest,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);

        List<RecommendationResponse> responses = recommendationService.getRecommendations(context.businessId(), queryRequest.month())
                .stream()
                .map(RecommendationResponse::fromEntity)
                .toList();

        return ResponseEntity.ok(responses);
    }
}
