package com.app.sme_health_backend.recurring.controller;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.recurring.dto.OccurrenceGenerateDueResponse;
import com.app.sme_health_backend.recurring.dto.OccurrencePreviewItem;
import com.app.sme_health_backend.recurring.dto.OccurrenceSkipRequest;
import com.app.sme_health_backend.recurring.dto.RecurringOccurrenceResponse;
import com.app.sme_health_backend.recurring.dto.RecurringRuleCreateRequest;
import com.app.sme_health_backend.recurring.dto.RecurringRuleResponse;
import com.app.sme_health_backend.recurring.dto.RecurringRuleUpdateRequest;
import com.app.sme_health_backend.recurring.model.RecurringOccurrenceStatus;
import com.app.sme_health_backend.recurring.model.RecurringRuleStatus;
import com.app.sme_health_backend.recurring.service.RecurringRuleService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/finance/recurring")
public class RecurringRuleController {

    private final RecurringRuleService recurringService;
    private final BusinessAuthorizationService authService;

    public RecurringRuleController(
            RecurringRuleService recurringService,
            BusinessAuthorizationService authService
    ) {
        this.recurringService = recurringService;
        this.authService = authService;
    }

    @PostMapping("/rules")
    public ResponseEntity<RecurringRuleResponse> createRule(
            @RequestBody RecurringRuleCreateRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_POST
        );
        RecurringRuleResponse response = recurringService.createRule(context, requestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/rules")
    public ResponseEntity<Page<RecurringRuleResponse>> listRules(
            @RequestParam(name = "status", required = false) RecurringRuleStatus status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_READ
        );
        int boundedPage = Math.max(page, 0);
        int boundedSize = Math.max(1, Math.min(size, 100));
        Pageable pageable = PageRequest.of(boundedPage, boundedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(recurringService.listRules(context, status, pageable));
    }

    @GetMapping("/rules/{id}")
    public ResponseEntity<RecurringRuleResponse> getRule(
            @PathVariable("id") UUID id,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_READ
        );
        return ResponseEntity.ok(recurringService.getRule(context, id));
    }

    @PutMapping("/rules/{id}")
    public ResponseEntity<RecurringRuleResponse> updateRule(
            @PathVariable("id") UUID id,
            @RequestBody RecurringRuleUpdateRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_POST
        );
        RecurringRuleResponse response = recurringService.updateRule(context, id, requestDto);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/rules/{id}/pause")
    public ResponseEntity<RecurringRuleResponse> pauseRule(
            @PathVariable("id") UUID id,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_POST
        );
        return ResponseEntity.ok(recurringService.pauseRule(context, id));
    }

    @PostMapping("/rules/{id}/resume")
    public ResponseEntity<RecurringRuleResponse> resumeRule(
            @PathVariable("id") UUID id,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_POST
        );
        return ResponseEntity.ok(recurringService.resumeRule(context, id));
    }

    @PostMapping("/rules/{id}/archive")
    public ResponseEntity<RecurringRuleResponse> archiveRule(
            @PathVariable("id") UUID id,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_POST
        );
        return ResponseEntity.ok(recurringService.archiveRule(context, id));
    }

    @GetMapping("/rules/{id}/preview")
    public ResponseEntity<List<OccurrencePreviewItem>> previewSchedule(
            @PathVariable("id") UUID id,
            @RequestParam(name = "count", defaultValue = "5") int count,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_READ
        );
        return ResponseEntity.ok(recurringService.previewSchedule(context, id, count));
    }

    @GetMapping("/occurrences")
    public ResponseEntity<Page<RecurringOccurrenceResponse>> listOccurrences(
            @RequestParam(name = "ruleId", required = false) UUID ruleId,
            @RequestParam(name = "status", required = false) RecurringOccurrenceStatus status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_READ
        );
        int boundedPage = Math.max(page, 0);
        int boundedSize = Math.max(1, Math.min(size, 100));
        Pageable pageable = PageRequest.of(boundedPage, boundedSize, Sort.by(Sort.Direction.DESC, "scheduledDate"));
        return ResponseEntity.ok(recurringService.listOccurrences(context, ruleId, status, pageable));
    }

    @GetMapping("/occurrences/{id}")
    public ResponseEntity<RecurringOccurrenceResponse> getOccurrence(
            @PathVariable("id") UUID id,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_READ
        );
        return ResponseEntity.ok(recurringService.getOccurrence(context, id));
    }

    @PostMapping("/occurrences/generate-due")
    public ResponseEntity<OccurrenceGenerateDueResponse> generateDueOccurrences(
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_POST
        );
        return ResponseEntity.ok(recurringService.generateDueOccurrencesForBusiness(context));
    }

    @PostMapping("/occurrences/{id}/skip")
    public ResponseEntity<RecurringOccurrenceResponse> skipOccurrence(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) OccurrenceSkipRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_POST
        );
        return ResponseEntity.ok(recurringService.skipOccurrence(context, id, requestDto));
    }
}
