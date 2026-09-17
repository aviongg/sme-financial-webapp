package com.app.sme_health_backend.search.controller;

import com.app.sme_health_backend.search.dto.SearchResultResponse;
import com.app.sme_health_backend.search.service.SearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<List<SearchResultResponse>> search(
            @PathVariable String userId,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "type", required = false, defaultValue = "all") String type
    ) {
        UUID parsedUserId;
        try {
            parsedUserId = UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid user ID format: " + userId);
        }

        List<SearchResultResponse> results = searchService.search(parsedUserId, query, type);
        return ResponseEntity.ok(results);
    }

    @GetMapping
    public ResponseEntity<List<SearchResultResponse>> searchWithQueryParam(
            @RequestParam(name = "userId") String userId,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "type", required = false, defaultValue = "all") String type
    ) {
        return search(userId, query, type);
    }
}
