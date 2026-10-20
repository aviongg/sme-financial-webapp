package com.app.sme_health_backend.reconciliation.controller;

import com.app.sme_health_backend.accounting.draft.DraftDetailResponse;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.reconciliation.dto.*;
import com.app.sme_health_backend.reconciliation.model.StatementEntryStatus;
import com.app.sme_health_backend.reconciliation.service.BankReconciliationService;
import com.app.sme_health_backend.reconciliation.service.BankStatementImportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/finance/reconciliation")
public class BankReconciliationController {

    private final BankStatementImportService importService;
    private final BankReconciliationService reconciliationService;
    private final BusinessAuthorizationService authService;

    public BankReconciliationController(
            BankStatementImportService importService,
            BankReconciliationService reconciliationService,
            BusinessAuthorizationService authService
    ) {
        this.importService = importService;
        this.reconciliationService = reconciliationService;
        this.authService = authService;
    }

    @PostMapping("/imports")
    public ResponseEntity<StatementImportResponse> importStatement(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody StatementImportRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_POST
        );
        StatementImportResponse response = importService.importStatement(context, idempotencyKey, requestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/imports")
    public ResponseEntity<List<StatementImportResponse>> listImports(
            @RequestParam("accountId") UUID accountId,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_READ
        );
        List<StatementImportResponse> response = importService.listImports(context, accountId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/imports/{id}")
    public ResponseEntity<StatementImportDetailResponse> getImport(
            @PathVariable("id") UUID id,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_READ
        );
        StatementImportDetailResponse response = importService.getImportDetail(context, id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/entries")
    public ResponseEntity<Page<StatementEntryResponse>> listEntries(
            @RequestParam(name = "accountId", required = false) UUID accountId,
            @RequestParam(name = "status", required = false) StatementEntryStatus status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_READ
        );
        Pageable pageable = PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "postedDate", "sourceRowNumber"));
        Page<StatementEntryResponse> response = reconciliationService.listEntries(context, accountId, status, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/entries/{id}")
    public ResponseEntity<StatementEntryDetailResponse> getEntryDetail(
            @PathVariable("id") UUID id,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_READ
        );
        StatementEntryDetailResponse response = reconciliationService.getEntryDetail(context, id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/entries/{id}/candidates")
    public ResponseEntity<List<ReconciliationMatchCandidateResponse>> getCandidates(
            @PathVariable("id") UUID id,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_READ
        );
        List<ReconciliationMatchCandidateResponse> response = reconciliationService.findCandidates(context, id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/entries/{id}/match")
    public ResponseEntity<StatementEntryDetailResponse> matchEntry(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ReconciliationMatchRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_POST
        );
        StatementEntryDetailResponse response = reconciliationService.matchEntry(context, id, requestDto);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/entries/{id}/unmatch")
    public ResponseEntity<StatementEntryDetailResponse> unmatchEntry(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) ReconciliationUnmatchRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_POST
        );
        StatementEntryDetailResponse response = reconciliationService.unmatchEntry(context, id, requestDto);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/entries/{id}/exclude")
    public ResponseEntity<StatementEntryDetailResponse> excludeEntry(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ReconciliationExcludeRequest requestDto,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_POST
        );
        StatementEntryDetailResponse response = reconciliationService.excludeEntry(context, id, requestDto);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/entries/{id}/draft")
    public ResponseEntity<DraftDetailResponse> createDraft(
            @PathVariable("id") UUID id,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_POST
        );
        DraftDetailResponse response = reconciliationService.createDraftFromEntry(context, id);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/accounts/{accountId}/summary")
    public ResponseEntity<AccountReconciliationSummaryResponse> getAccountSummary(
            @PathVariable("accountId") UUID accountId,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_READ
        );
        AccountReconciliationSummaryResponse response = reconciliationService.getAccountSummary(context, accountId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/accounts/{accountId}/unmatched-movements")
    public ResponseEntity<List<UnmatchedMovementResponse>> getUnmatchedMovements(
            @PathVariable("accountId") UUID accountId,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_READ
        );
        List<UnmatchedMovementResponse> response = reconciliationService.getUnmatchedMovements(context, accountId);
        return ResponseEntity.ok(response);
    }
}
