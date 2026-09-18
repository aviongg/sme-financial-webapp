package com.app.sme_health_backend.zakat.controller;

import com.app.sme_health_backend.zakat.dto.MonthlyRecordZakatRequest;
import com.app.sme_health_backend.zakat.dto.ZakatPreviewRequest;
import com.app.sme_health_backend.zakat.dto.ZakatPreviewResponse;
import com.app.sme_health_backend.zakat.service.ZakatService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/zakat")
public class ZakatController {
    private final ZakatService zakatService;

    public ZakatController(ZakatService zakatService) {
        this.zakatService = zakatService;
    }

    @PostMapping("/preview")
    public ZakatPreviewResponse preview(@RequestBody ZakatPreviewRequest request) {
        return zakatService.preview(request);
    }

    @PostMapping("/{userId}/{month}/preview")
    public ZakatPreviewResponse previewMonthlyRecord(
            @PathVariable UUID userId,
            @PathVariable String month,
            @RequestBody MonthlyRecordZakatRequest request
    ) {
        return zakatService.previewMonthlyRecord(userId, month, request);
    }
}
