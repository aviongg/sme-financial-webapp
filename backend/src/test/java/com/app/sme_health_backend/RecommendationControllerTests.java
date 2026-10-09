package com.app.sme_health_backend;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.recommendation.controller.RecommendationController;
import com.app.sme_health_backend.recommendation.entity.Recommendation;
import com.app.sme_health_backend.recommendation.service.RecommendationService;
import com.app.sme_health_backend.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RecommendationController.class)
@Import(GlobalExceptionHandler.class)
class RecommendationControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecommendationService recommendationService;

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
    void shouldReturnRecommendationsForUser() throws Exception {
        Recommendation recommendation = new Recommendation();
        recommendation.setUserId(businessId);
        recommendation.setMonth("2026-09");
        recommendation.setText("Review cash collection timing.");
        recommendation.setCategory("cashflow");
        recommendation.setPriority("high");
        recommendation.setCreatedAt(LocalDateTime.now());

        when(recommendationService.getRecommendations(businessId))
                .thenReturn(List.of(recommendation));

        mockMvc.perform(get("/api/recommendations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].businessId").value(businessId.toString()))
                .andExpect(jsonPath("$[0].month").value("2026-09"))
                .andExpect(jsonPath("$[0].text").value("Review cash collection timing."))
                .andExpect(jsonPath("$[0].category").value("cashflow"))
                .andExpect(jsonPath("$[0].priority").value("high"));
    }

    @Test
    void shouldReturnEmptyListWhenServiceReturnsNoRecommendations() throws Exception {
        when(recommendationService.getRecommendations(businessId))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/recommendations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldReturnRecommendationsForSpecificMonth() throws Exception {
        Recommendation recommendation = new Recommendation();
        recommendation.setUserId(businessId);
        recommendation.setMonth("2026-08");
        recommendation.setText("Review cash collection timing.");
        recommendation.setCategory("cashflow");
        recommendation.setPriority("high");
        recommendation.setCreatedAt(LocalDateTime.now());

        when(recommendationService.getRecommendations(businessId, "2026-08")).thenReturn(List.of(recommendation));

        mockMvc.perform(post("/api/recommendations/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"2026-08\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].month").value("2026-08"))
                .andExpect(jsonPath("$[0].category").value("cashflow"));
    }

    @Test
    void shouldRejectLegacyRecommendationsWithUserIdUrl() throws Exception {
        mockMvc.perform(get("/api/recommendations/{userId}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
