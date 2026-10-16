package com.app.sme_health_backend.accounting.draft;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Controller exposing endpoints for managing persistent record drafts and safe recording.
 */
@RestController
@RequestMapping("/api/finance/drafts")
public class RecordDraftController {

    private final RecordDraftService draftService;
    private final BusinessAuthorizationService authService;

    public RecordDraftController(
            RecordDraftService draftService,
            BusinessAuthorizationService authService
    ) {
        this.draftService = draftService;
        this.authService = authService;
    }

    @PostMapping
    public ResponseEntity<DraftDetailResponse> createDraft(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) DraftCreateRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_POST
        );

        DraftDetailResponse response = draftService.createDraft(
                context, idempotencyKey, requestDto
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<DraftListResponse> listDrafts(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "accountId", required = false) UUID accountId,
            @RequestParam(name = "eventType", required = false) String eventType,
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "20") int size,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_READ
        );

        int validPage = Math.max(0, page);
        int validSize = Math.max(1, Math.min(100, size));

        DraftListResponse response = draftService.listDrafts(
                context, status, accountId, eventType, PageRequest.of(validPage, validSize)
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id:[0-9a-fA-F\\-]{36}}")
    public ResponseEntity<DraftDetailResponse> getDraft(
            @PathVariable("id") UUID id,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_READ
        );

        DraftDetailResponse response = draftService.getDraft(context, id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id:[0-9a-fA-F\\-]{36}}")
    public ResponseEntity<DraftDetailResponse> updateDraft(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) DraftUpdateRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_POST
        );

        DraftDetailResponse response = draftService.updateDraft(context, id, requestDto);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id:[0-9a-fA-F\\-]{36}}/discard")
    public ResponseEntity<DraftDetailResponse> discardDraft(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) DraftDiscardRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_POST
        );

        DraftDetailResponse response = draftService.discardDraft(context, id, requestDto);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id:[0-9a-fA-F\\-]{36}}/record")
    public ResponseEntity<DraftRecordResponse> recordDraft(
            @PathVariable("id") UUID id,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) DraftRecordRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_POST
        );

        DraftRecordResponse response = draftService.recordDraft(
                context, id, idempotencyKey, requestDto
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
