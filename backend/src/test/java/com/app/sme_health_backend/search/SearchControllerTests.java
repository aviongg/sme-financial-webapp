package com.app.sme_health_backend.search;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.search.controller.SearchController;
import com.app.sme_health_backend.search.dto.SearchResultResponse;
import com.app.sme_health_backend.search.service.SearchService;
import com.app.sme_health_backend.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchController.class)
@Import(GlobalExceptionHandler.class)
class SearchControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SearchService searchService;

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
    @DisplayName("Matching query returns 200 with result list")
    void shouldReturn200WithResultsWhenQueryMatches() throws Exception {
        SearchResultResponse item = new SearchResultResponse(
                "res-1",
                "August 2026 Monthly Record",
                "Revenue: PKR 2,100,000",
                "transaction",
                "2026-08",
                new BigDecimal("2100000"),
                "Monthly Financial Record",
                "/records/res-1",
                "Official"
        );

        when(searchService.search(eq(businessId), eq("August"), eq("all")))
                .thenReturn(List.of(item));

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"August\",\"type\":\"all\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("res-1"))
                .andExpect(jsonPath("$[0].title").value("August 2026 Monthly Record"))
                .andExpect(jsonPath("$[0].type").value("transaction"))
                .andExpect(jsonPath("$[0].date").value("2026-08"))
                .andExpect(jsonPath("$[0].amount").value(2100000))
                .andExpect(jsonPath("$[0].badge").value("Official"));
    }

    @Test
    @DisplayName("Blank query returns 200 with empty array []")
    void shouldReturn200WithEmptyListWhenQueryIsBlank() throws Exception {
        when(searchService.search(eq(businessId), eq(""), eq("all")))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"\",\"type\":\"all\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("No matches returns 200 with empty array []")
    void shouldReturn200WithEmptyListWhenNoMatchesFound() throws Exception {
        when(searchService.search(eq(businessId), eq("nonexistentterm123"), eq("all")))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"nonexistentterm123\",\"type\":\"all\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Legacy GET /api/search/{userId} returns 404")
    void shouldRejectLegacySearchWithUserIdUrl() throws Exception {
        mockMvc.perform(get("/api/search/{userId}", UUID.randomUUID())
                        .param("q", "August"))
                .andExpect(status().isNotFound());
    }
}
