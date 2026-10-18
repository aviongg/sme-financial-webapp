package com.app.sme_health_backend.accounting.draft.imports;

import com.app.sme_health_backend.accounting.draft.imports.dto.ImportBatchDetailResponse;
import com.app.sme_health_backend.accounting.draft.imports.dto.ImportBatchListResponse;
import com.app.sme_health_backend.accounting.draft.imports.dto.ImportDraftsRequest;
import com.app.sme_health_backend.accounting.draft.imports.dto.ImportDraftsResponse;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Controller exposing endpoints for saving spreadsheet import rows as drafts
 * and recovering import batch provenance.
 */
@RestController
@RequestMapping("/api/finance/drafts/import")
public class ImportDraftController {

    private final ImportDraftService importDraftService;
    private final BusinessAuthorizationService authService;

    public ImportDraftController(
            ImportDraftService importDraftService,
            BusinessAuthorizationService authService
    ) {
        this.importDraftService = importDraftService;
        this.authService = authService;
    }

    @PostMapping
    public ResponseEntity<ImportDraftsResponse> saveImportDrafts(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) ImportDraftsRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_POST
        );

        ImportDraftsResponse response = importDraftService.saveImportDrafts(
                context, idempotencyKey, requestDto
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{batchId:[0-9a-fA-F\\-]{36}}")
    public ResponseEntity<ImportBatchDetailResponse> getImportBatch(
            @PathVariable("batchId") UUID batchId,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_READ
        );

        ImportBatchDetailResponse response = importDraftService.getImportBatch(context, batchId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<ImportBatchListResponse> listImportBatches(
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "20") int size,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_READ
        );

        int validPage = Math.max(0, page);
        int validSize = Math.max(1, Math.min(100, size));

        ImportBatchListResponse response = importDraftService.listImportBatches(
                context, PageRequest.of(validPage, validSize)
        );
        return ResponseEntity.ok(response);
    }
}
