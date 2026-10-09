package com.app.sme_health_backend.dashboard.controller;

import com.app.sme_health_backend.dashboard.dto.DashboardResponse;
import com.app.sme_health_backend.dashboard.service.DashboardService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final BusinessAuthorizationService authService;

    public DashboardController(
            DashboardService dashboardService,
            BusinessAuthorizationService authService
    ) {
        this.dashboardService = dashboardService;
        this.authService = authService;
    }

    @GetMapping
    public ResponseEntity<DashboardResponse> getDashboard(HttpServletRequest request) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);

        DashboardResponse response = dashboardService.getDashboard(context.businessId());
        return ResponseEntity.ok(response);
    }
}
