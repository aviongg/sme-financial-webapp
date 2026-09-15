package com.app.sme_health_backend.scoring.controller;

import com.app.sme_health_backend.scoring.dto.ScoreResultResponse;
import com.app.sme_health_backend.scoring.entity.ScoreResult;
import com.app.sme_health_backend.scoring.service.ScoringService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/scores")
public class ScoreController {

    private final ScoringService scoringService;

    public ScoreController(ScoringService scoringService) {
        this.scoringService = scoringService;
    }

    @PostMapping("/calculate/{userId}/{month}")
    public ResponseEntity<ScoreResultResponse> calculateScore(
            @PathVariable UUID userId,
            @PathVariable String month
    ) {
        ScoreResult result = scoringService.calculateAndSaveScore(userId, month);
        return ResponseEntity.ok(ScoreResultResponse.fromEntity(result));
    }

    @GetMapping("/{userId}/{month}")
    public ResponseEntity<ScoreResultResponse> getScore(
            @PathVariable UUID userId,
            @PathVariable String month
    ) {
        return scoringService.getScore(userId, month)
                .map(ScoreResultResponse::fromEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{userId}/latest")
    public ResponseEntity<ScoreResultResponse> getLatestScore(
            @PathVariable UUID userId
    ) {
        return scoringService.getLatestScore(userId)
                .map(ScoreResultResponse::fromEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
