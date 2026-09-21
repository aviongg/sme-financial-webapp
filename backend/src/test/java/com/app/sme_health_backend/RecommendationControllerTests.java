package com.app.sme_health_backend;

import com.app.sme_health_backend.recommendation.controller.RecommendationController;
import com.app.sme_health_backend.recommendation.entity.Recommendation;
import com.app.sme_health_backend.recommendation.service.RecommendationService;
import com.app.sme_health_backend.shared.exception.GlobalExceptionHandler;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RecommendationController.class)
@Import(GlobalExceptionHandler.class)
class RecommendationControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecommendationService recommendationService;

    @Test
    void shouldReturnRecommendationsForUser() throws Exception {
        UUID userId = UUID.randomUUID();
        Recommendation recommendation = new Recommendation();

        recommendation.setUserId(userId);
        recommendation.setMonth("2026-09");
        recommendation.setText("Review cash collection timing.");
        recommendation.setCategory("cashflow");
        recommendation.setPriority("high");
        recommendation.setCreatedAt(LocalDateTime.now());
        recommendation.setLanguage("en");
        recommendation.setSourceVersion("a".repeat(64));
        recommendation.setSourceComputedAt(LocalDateTime.of(2026, 9, 15, 12, 30));

        when(recommendationService.getRecommendations(userId, null))
                .thenReturn(List.of(recommendation));

        mockMvc.perform(get("/api/recommendations/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId")
                        .value(userId.toString()))
                .andExpect(jsonPath("$[0].month").value("2026-09"))
                .andExpect(jsonPath("$[0].text")
                        .value("Review cash collection timing."))
                .andExpect(jsonPath("$[0].category")
                        .value("cashflow"))
                .andExpect(jsonPath("$[0].priority").value("high"))
                .andExpect(jsonPath("$[0].language").value("en"))
                .andExpect(jsonPath("$[0].sourceVersion").value("a".repeat(64)))
                .andExpect(jsonPath("$[0].sourceComputedAt").value("2026-09-15T12:30:00"));
        verify(recommendationService).getRecommendations(userId, null);
    }

    @Test
    void shouldReturnEmptyListWhenServiceReturnsNoRecommendations()
            throws Exception {
        UUID userId = UUID.randomUUID();

        when(recommendationService.getRecommendations(userId, null))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/recommendations/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()")
                        .value(0));
    }

    @Test
    void shouldPassExactMonthFilterToService() throws Exception {
        UUID userId = UUID.randomUUID();
        when(recommendationService.getRecommendations(userId, "2026-08"))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/recommendations/{userId}", userId).param("month", "2026-08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(recommendationService).getRecommendations(userId, "2026-08");
    }

    @Test
    void shouldReturnBadRequestForInvalidMonth() throws Exception {
        UUID userId = UUID.randomUUID();
        when(recommendationService.getRecommendations(userId, "2026-13"))
                .thenThrow(new IllegalArgumentException("Month must be in YYYY-MM format"));

        mockMvc.perform(get("/api/recommendations/{userId}", userId).param("month", "2026-13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Month must be in YYYY-MM format"));
    }

    @Test
    void shouldReturnNotFoundForUnknownProfile() throws Exception {
        UUID userId = UUID.randomUUID();
        when(recommendationService.getRecommendations(userId, null))
                .thenThrow(new ResourceNotFoundException("Business profile not found for this user"));

        mockMvc.perform(get("/api/recommendations/{userId}", userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Business profile not found for this user"));
    }
}
