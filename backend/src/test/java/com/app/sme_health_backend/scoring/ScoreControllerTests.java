package com.app.sme_health_backend.scoring;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.scoring.controller.ScoreController;
import com.app.sme_health_backend.scoring.dto.ComponentScoresDto;
import com.app.sme_health_backend.scoring.entity.ScoreResult;
import com.app.sme_health_backend.scoring.service.ScoringService;
import com.app.sme_health_backend.shared.exception.GlobalExceptionHandler;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
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

    @MockitoBean
    private BusinessAuthorizationService authService;

    private final UUID userId = UUID.randomUUID();
    private final UUID businessId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
        when(authService.requirePermission(any(), any(BusinessPermission.class))).thenReturn(context);
    }

    @Test
    void shouldCalculateAndReturnScore() throws Exception {
        ScoreResult result = createScoreResult(businessId, "2026-09", new BigDecimal("75.00"), "Stable");

        when(scoringService.calculateAndSaveScore(businessId, "2026-09")).thenReturn(result);

        mockMvc.perform(post("/api/scores/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"2026-09\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessId").value(businessId.toString()))
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
        when(scoringService.calculateAndSaveScore(businessId, "2026-09"))
                .thenThrow(new ResourceNotFoundException("Monthly record not found for user and month: 2026-09"));

        mockMvc.perform(post("/api/scores/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"2026-09\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Monthly record not found for user and month: 2026-09"));
    }

    @Test
    void shouldReturnBadRequestWhenInsufficientData() throws Exception {
        when(scoringService.calculateAndSaveScore(businessId, "2026-09"))
                .thenThrow(new IllegalArgumentException("Insufficient financial data to calculate financial health score"));

        mockMvc.perform(post("/api/scores/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"2026-09\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Insufficient financial data to calculate financial health score"));
    }

    @Test
    void shouldGetMonthlyScore() throws Exception {
        ScoreResult result = createScoreResult(businessId, "2026-09", new BigDecimal("85.00"), "Strong");

        when(scoringService.getScore(businessId, "2026-09")).thenReturn(Optional.of(result));

        mockMvc.perform(post("/api/scores/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"2026-09\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessId").value(businessId.toString()))
                .andExpect(jsonPath("$.month").value("2026-09"))
                .andExpect(jsonPath("$.compositeScore").value(85.00))
                .andExpect(jsonPath("$.band").value("Strong"));
    }

    @Test
    void shouldReturnNotFoundWhenMonthlyScoreDoesNotExist() throws Exception {
        when(scoringService.getScore(businessId, "2026-09")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/scores/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"2026-09\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldGetLatestScore() throws Exception {
        ScoreResult result = createScoreResult(businessId, "2026-09", new BigDecimal("90.00"), "Strong");

        when(scoringService.getLatestScore(businessId)).thenReturn(Optional.of(result));

        mockMvc.perform(get("/api/scores/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessId").value(businessId.toString()))
                .andExpect(jsonPath("$.month").value("2026-09"))
                .andExpect(jsonPath("$.compositeScore").value(90.00))
                .andExpect(jsonPath("$.band").value("Strong"));
    }

    @Test
    void shouldReturnNotFoundWhenLatestScoreDoesNotExist() throws Exception {
        when(scoringService.getLatestScore(businessId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/scores/latest"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectLegacyCalculateWithUserIdUrl() throws Exception {
        mockMvc.perform(post("/api/scores/calculate/{userId}/2026-09", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectLegacyQueryWithUserIdUrl() throws Exception {
        mockMvc.perform(get("/api/scores/{userId}/2026-09", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectLegacyLatestWithUserIdUrl() throws Exception {
        mockMvc.perform(get("/api/scores/{userId}/latest", UUID.randomUUID()))
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
