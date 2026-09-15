package com.app.sme_health_backend.scoring;

import com.app.sme_health_backend.scoring.controller.ScoreController;
import com.app.sme_health_backend.scoring.dto.ComponentScoresDto;
import com.app.sme_health_backend.scoring.entity.ScoreResult;
import com.app.sme_health_backend.scoring.service.ScoringService;
import com.app.sme_health_backend.shared.exception.GlobalExceptionHandler;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ScoreController.class)
@Import(GlobalExceptionHandler.class)
class ScoreControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScoringService scoringService;

    @Test
    void shouldCalculateAndReturnScore() throws Exception {
        UUID userId = UUID.randomUUID();
        ScoreResult result = createScoreResult(userId, "2026-09", new BigDecimal("75.00"), "Stable");

        when(scoringService.calculateAndSaveScore(userId, "2026-09")).thenReturn(result);

        mockMvc.perform(post("/api/scores/calculate/{userId}/2026-09", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.month").value("2026-09"))
                .andExpect(jsonPath("$.compositeScore").value(75.00))
                .andExpect(jsonPath("$.band").value("Stable"))
                .andExpect(jsonPath("$.weakestComponent").value("profitability"))
                .andExpect(jsonPath("$.dataCompleteness").value(0.55))
                .andExpect(jsonPath("$.componentScores.cashflow").value(80.00))
                .andExpect(jsonPath("$.componentScores.profitability").value(60.00));
    }

    @Test
    void shouldReturnNotFoundWhenRecordDoesNotExist() throws Exception {
        UUID userId = UUID.randomUUID();

        when(scoringService.calculateAndSaveScore(userId, "2026-09"))
                .thenThrow(new ResourceNotFoundException("Monthly record not found for user and month: 2026-09"));

        mockMvc.perform(post("/api/scores/calculate/{userId}/2026-09", userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Monthly record not found for user and month: 2026-09"));
    }

    @Test
    void shouldReturnBadRequestWhenInsufficientData() throws Exception {
        UUID userId = UUID.randomUUID();

        when(scoringService.calculateAndSaveScore(userId, "2026-09"))
                .thenThrow(new IllegalArgumentException("Insufficient financial data to calculate financial health score"));

        mockMvc.perform(post("/api/scores/calculate/{userId}/2026-09", userId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Insufficient financial data to calculate financial health score"));
    }

    @Test
    void shouldGetMonthlyScore() throws Exception {
        UUID userId = UUID.randomUUID();
        ScoreResult result = createScoreResult(userId, "2026-09", new BigDecimal("85.00"), "Strong");

        when(scoringService.getScore(userId, "2026-09")).thenReturn(Optional.of(result));

        mockMvc.perform(get("/api/scores/{userId}/2026-09", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.month").value("2026-09"))
                .andExpect(jsonPath("$.compositeScore").value(85.00))
                .andExpect(jsonPath("$.band").value("Strong"));
    }

    @Test
    void shouldReturnNotFoundWhenMonthlyScoreDoesNotExist() throws Exception {
        UUID userId = UUID.randomUUID();

        when(scoringService.getScore(userId, "2026-09")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/scores/{userId}/2026-09", userId))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldGetLatestScore() throws Exception {
        UUID userId = UUID.randomUUID();
        ScoreResult result = createScoreResult(userId, "2026-09", new BigDecimal("90.00"), "Strong");

        when(scoringService.getLatestScore(userId)).thenReturn(Optional.of(result));

        mockMvc.perform(get("/api/scores/{userId}/latest", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.month").value("2026-09"))
                .andExpect(jsonPath("$.compositeScore").value(90.00))
                .andExpect(jsonPath("$.band").value("Strong"));
    }

    @Test
    void shouldReturnNotFoundWhenLatestScoreDoesNotExist() throws Exception {
        UUID userId = UUID.randomUUID();

        when(scoringService.getLatestScore(userId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/scores/{userId}/latest", userId))
                .andExpect(status().isNotFound());
    }

    private ScoreResult createScoreResult(UUID userId, String month, BigDecimal compositeScore, String band) {
        ScoreResult result = new ScoreResult();
        result.setId(UUID.randomUUID());
        result.setUserId(userId);
        result.setMonth(month);
        result.setCompositeScore(compositeScore);
        result.setBand(band);
        result.setComponentScores(new ComponentScoresDto(
                new BigDecimal("80.00"),
                new BigDecimal("60.00"),
                null,
                null,
                null
        ));
        result.setWeakestComponent("profitability");
        result.setDataCompleteness(new BigDecimal("0.55"));
        result.setComputedAt(LocalDateTime.now());
        return result;
    }
}
