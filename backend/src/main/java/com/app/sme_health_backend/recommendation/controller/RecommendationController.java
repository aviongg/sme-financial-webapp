package com.app.sme_health_backend.recommendation.controller;

import com.app.sme_health_backend.recommendation.dto.RecommendationResponse;
import com.app.sme_health_backend.recommendation.service.RecommendationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/{userId}")
    public List<RecommendationResponse> getRecommendations(
            @PathVariable UUID userId,
            @RequestParam(required = false) String month
    ) {
        return (month == null
                ? recommendationService.getRecommendations(userId)
                : recommendationService.getRecommendations(userId, month))
                .stream()
                .map(RecommendationResponse::fromEntity)
                .toList();
    }
}
