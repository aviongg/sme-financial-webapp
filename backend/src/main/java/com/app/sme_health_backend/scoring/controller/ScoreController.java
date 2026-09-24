package com.app.sme_health_backend.scoring.controller;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.scoring.dto.ScoreResultResponse;
import com.app.sme_health_backend.scoring.entity.ScoreResult;
import com.app.sme_health_backend.scoring.service.ScoringService;
import com.app.sme_health_backend.shared.dto.MonthQueryRequest;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/scores")
public class ScoreController {

    private final ScoringService scoringService;
    private final BusinessAuthorizationService authService;

    public ScoreController(
            ScoringService scoringService,
            BusinessAuthorizationService authService
    ) {
        this.scoringService = scoringService;
        this.authService = authService;
    }

    @PostMapping("/calculate")
    public ResponseEntity<ScoreResultResponse> calculateScore(
            @Valid @RequestBody MonthQueryRequest queryRequest,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.SCORE_CALCULATE);

        ScoreResult result = scoringService.calculateAndSaveScore(context.businessId(), queryRequest.month());
        return ResponseEntity.ok(ScoreResultResponse.fromEntity(result));
    }

    @PostMapping("/query")
    public ResponseEntity<ScoreResultResponse> queryScore(
            @Valid @RequestBody MonthQueryRequest queryRequest,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);

        return scoringService.getScore(context.businessId(), queryRequest.month())
                .map(ScoreResultResponse::fromEntity)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Score not found for month: " + queryRequest.month()
                ));
    }

    @GetMapping("/latest")
    public ResponseEntity<ScoreResultResponse> getLatestScore(HttpServletRequest request) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);

        return scoringService.getLatestScore(context.businessId())
                .map(ScoreResultResponse::fromEntity)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("No score available for this business"));
    }
}
