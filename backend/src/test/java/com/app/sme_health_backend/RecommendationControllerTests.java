package com.app.sme_health_backend;

import com.app.sme_health_backend.recommendation.controller.RecommendationController;
import com.app.sme_health_backend.recommendation.entity.Recommendation;
import com.app.sme_health_backend.recommendation.service.RecommendationService;
import com.app.sme_health_backend.shared.exception.GlobalExceptionHandler;
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

        when(recommendationService.getRecommendations(userId))
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
                .andExpect(jsonPath("$[0].priority").value("high"));
    }

    @Test
    void shouldReturnEmptyListWhenServiceReturnsNoRecommendations()
            throws Exception {
        UUID userId = UUID.randomUUID();

        when(recommendationService.getRecommendations(userId))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/recommendations/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()")
                        .value(0));
    }
}
