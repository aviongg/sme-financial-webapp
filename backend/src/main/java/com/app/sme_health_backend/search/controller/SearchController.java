package com.app.sme_health_backend.search.controller;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.search.dto.SearchRequest;
import com.app.sme_health_backend.search.dto.SearchResultResponse;
import com.app.sme_health_backend.search.service.SearchService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;
    private final BusinessAuthorizationService authService;

    public SearchController(
            SearchService searchService,
            BusinessAuthorizationService authService
    ) {
        this.searchService = searchService;
        this.authService = authService;
    }

    @PostMapping
    public ResponseEntity<List<SearchResultResponse>> search(
            @Valid @RequestBody SearchRequest searchRequest,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);

        List<SearchResultResponse> results = searchService.search(
                context.businessId(),
                searchRequest.query(),
                searchRequest.type()
        );
        return ResponseEntity.ok(results);
    }
}
