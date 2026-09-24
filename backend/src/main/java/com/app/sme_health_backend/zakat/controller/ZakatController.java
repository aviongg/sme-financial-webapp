package com.app.sme_health_backend.zakat.controller;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.zakat.dto.MonthlyRecordZakatRequest;
import com.app.sme_health_backend.zakat.dto.ZakatPreviewRequest;
import com.app.sme_health_backend.zakat.dto.ZakatPreviewResponse;
import com.app.sme_health_backend.zakat.service.ZakatService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/zakat")
public class ZakatController {

    private final ZakatService zakatService;
    private final BusinessAuthorizationService authService;

    public ZakatController(
            ZakatService zakatService,
            BusinessAuthorizationService authService
    ) {
        this.zakatService = zakatService;
        this.authService = authService;
    }

    /**
     * Authenticated stateless calculation: does NOT require an active business.
     */
    @PostMapping("/preview")
    public ZakatPreviewResponse preview(@RequestBody ZakatPreviewRequest request) {
        return zakatService.preview(request);
    }

    /**
     * Tenant-derived monthly Zakat calculation: requires active business + ZAKAT_READ_CALCULATE.
     */
    @PostMapping("/monthly/preview")
    public ZakatPreviewResponse previewMonthlyRecord(
            @RequestBody MonthlyRecordZakatRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(request, BusinessPermission.ZAKAT_READ_CALCULATE);

        String month = requestDto != null ? requestDto.month() : null;
        if (month == null || month.isBlank()) {
            throw new IllegalArgumentException("Month is required in request body");
        }

        return zakatService.previewMonthlyRecord(context.businessId(), month, requestDto);
    }
}
