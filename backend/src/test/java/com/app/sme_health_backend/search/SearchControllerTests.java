package com.app.sme_health_backend.search;

import com.app.sme_health_backend.search.controller.SearchController;
import com.app.sme_health_backend.search.dto.SearchResultResponse;
import com.app.sme_health_backend.search.service.SearchService;
import com.app.sme_health_backend.shared.exception.GlobalExceptionHandler;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SearchController.class)
@Import(GlobalExceptionHandler.class)
class SearchControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SearchService searchService;

    @Test
    @DisplayName("Matching query returns 200 with result list")
    void shouldReturn200WithResultsWhenQueryMatches() throws Exception {
        UUID userId = UUID.randomUUID();
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

        when(searchService.search(eq(userId), eq("August"), eq("all")))
                .thenReturn(List.of(item));

        mockMvc.perform(get("/api/search/{userId}", userId)
                        .param("q", "August")
                        .param("type", "all"))
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
        UUID userId = UUID.randomUUID();

        when(searchService.search(eq(userId), eq(""), eq("all")))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/search/{userId}", userId)
                        .param("q", "")
                        .param("type", "all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("No matches returns 200 with empty array []")
    void shouldReturn200WithEmptyListWhenNoMatchesFound() throws Exception {
        UUID userId = UUID.randomUUID();

        when(searchService.search(eq(userId), eq("nonexistentterm123"), eq("all")))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/search/{userId}", userId)
                        .param("q", "nonexistentterm123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Malformed UUID returns 400 Bad Request")
    void shouldReturn400WhenUserIdIsMalformed() throws Exception {
        mockMvc.perform(get("/api/search/{userId}", "invalid-uuid-format")
                        .param("q", "August"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Invalid user ID format: invalid-uuid-format"));
    }

    @Test
    @DisplayName("Missing business profile returns 404 Not Found")
    void shouldReturn404WhenProfileNotFound() throws Exception {
        UUID userId = UUID.randomUUID();

        when(searchService.search(eq(userId), eq("August"), eq("all")))
                .thenThrow(new ResourceNotFoundException("Business profile not found for user: " + userId));

        mockMvc.perform(get("/api/search/{userId}", userId)
                        .param("q", "August"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Business profile not found for user: " + userId));
    }

    @Test
    @DisplayName("Passes type filter parameter correctly to service")
    void shouldPassTypeFilterParameter() throws Exception {
        UUID userId = UUID.randomUUID();

        when(searchService.search(eq(userId), eq("cash"), eq("insight")))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/search/{userId}", userId)
                        .param("q", "cash")
                        .param("type", "insight"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
