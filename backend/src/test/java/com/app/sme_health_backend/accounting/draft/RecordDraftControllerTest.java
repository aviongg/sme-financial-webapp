package com.app.sme_health_backend.accounting.draft;

import com.app.sme_health_backend.accounting.dto.MovementDetailResponse;
import com.app.sme_health_backend.accounting.dto.TransactionDetailResponse;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.shared.exception.DuplicateResourceException;
import com.app.sme_health_backend.shared.exception.GlobalExceptionHandler;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RecordDraftController.class)
@Import(GlobalExceptionHandler.class)
class RecordDraftControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecordDraftService draftService;

    @MockitoBean
    private BusinessAuthorizationService authService;

    private UUID businessId;
    private UUID userId;
    private UUID draftId;
    private UUID accountId;

    @BeforeEach
    void setUp() {
        businessId = UUID.randomUUID();
        userId = UUID.randomUUID();
        draftId = UUID.randomUUID();
        accountId = UUID.randomUUID();
    }

    @Test
    @DisplayName("POST /api/finance/drafts returns 201 Created for OWNER")
    void createDraft_ownerSuccess() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST))).thenReturn(context);

        DraftDetailResponse response = new DraftDetailResponse(
                draftId,
                accountId,
                "DIRECT_SALE_RECEIPT",
                "2026-10-15",
                "1500.",
                "Unfinished retail receipt",
                "DRAFT",
                false,
                0L,
                null,
                userId,
                userId,
                Instant.now(),
                Instant.now()
        );

        when(draftService.createDraft(eq(context), eq("IDEM-123"), any(DraftCreateRequest.class))).thenReturn(response);

        String json = """
                {
                  "accountId": "%s",
                  "eventType": "DIRECT_SALE_RECEIPT",
                  "effectiveDate": "2026-10-15",
                  "amountInput": "1500.",
                  "description": "Unfinished retail receipt"
                }
                """.formatted(accountId);

        mockMvc.perform(post("/api/finance/drafts")
                        .header("Idempotency-Key", "IDEM-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(draftId.toString()))
                .andExpect(jsonPath("$.amountInput").value("1500."))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.readyToRecord").value(false))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    @DisplayName("POST /api/finance/drafts returns 403 Forbidden for MANAGER")
    void createDraft_forbiddenForManager() throws Exception {
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST)))
                .thenThrow(new AccessDeniedException("Role MANAGER does not have permission: FINANCIAL_TRANSACTION_POST"));

        mockMvc.perform(post("/api/finance/drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/finance/drafts returns 403 Forbidden for VIEWER")
    void createDraft_forbiddenForViewer() throws Exception {
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST)))
                .thenThrow(new AccessDeniedException("Role VIEWER does not have permission: FINANCIAL_TRANSACTION_POST"));

        mockMvc.perform(post("/api/finance/drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/finance/drafts returns 400 Bad Request on DraftValidationException")
    void createDraft_validationError() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.ACCOUNTANT);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST))).thenReturn(context);

        when(draftService.createDraft(eq(context), any(), any()))
                .thenThrow(new DraftValidationException("amountInput", "Amount input exceeds maximum length of 100 characters"));

        mockMvc.perform(post("/api/finance/drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountInput\":\"very-long\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amountInput").value("Amount input exceeds maximum length of 100 characters"));
    }

    @Test
    @DisplayName("GET /api/finance/drafts returns 200 OK for VIEWER")
    void listDrafts_viewerSuccess() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.VIEWER);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_READ))).thenReturn(context);

        DraftSummaryItem item = new DraftSummaryItem(
                draftId,
                accountId,
                "DIRECT_SALE_RECEIPT",
                "2026-10-15",
                "1500.",
                "Unfinished",
                "DRAFT",
                false,
                0L,
                null,
                userId,
                userId,
                Instant.now(),
                Instant.now()
        );
        DraftListResponse response = new DraftListResponse(List.of(item), 0, 20, 1L, 1);

        when(draftService.listDrafts(eq(context), eq("DRAFT"), eq(accountId), eq("DIRECT_SALE_RECEIPT"), any(Pageable.class)))
                .thenReturn(response);

        mockMvc.perform(get("/api/finance/drafts")
                        .param("status", "DRAFT")
                        .param("accountId", accountId.toString())
                        .param("eventType", "DIRECT_SALE_RECEIPT")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(draftId.toString()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].amountInput").value("1500."));
    }

    @Test
    @DisplayName("GET /api/finance/drafts/{id} returns 200 OK when found")
    void getDraft_found() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.VIEWER);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_READ))).thenReturn(context);

        DraftDetailResponse response = new DraftDetailResponse(
                draftId,
                accountId,
                "DIRECT_SALE_RECEIPT",
                "2026-10-15",
                "2500.00",
                "Ready to record draft",
                "DRAFT",
                true,
                1L,
                null,
                userId,
                userId,
                Instant.now(),
                Instant.now()
        );

        when(draftService.getDraft(context, draftId)).thenReturn(response);

        mockMvc.perform(get("/api/finance/drafts/{id}", draftId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(draftId.toString()))
                .andExpect(jsonPath("$.readyToRecord").value(true))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    @DisplayName("GET /api/finance/drafts/{id} returns 404 Not Found when absent")
    void getDraft_notFound() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.VIEWER);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_READ))).thenReturn(context);

        when(draftService.getDraft(context, draftId))
                .thenThrow(new ResourceNotFoundException("Record draft not found"));

        mockMvc.perform(get("/api/finance/drafts/{id}", draftId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /api/finance/drafts/{id} returns 200 OK for OWNER")
    void updateDraft_ownerSuccess() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST))).thenReturn(context);

        DraftDetailResponse response = new DraftDetailResponse(
                draftId,
                accountId,
                "DIRECT_SALE_RECEIPT",
                "2026-10-15",
                "3000.00",
                "Updated draft",
                "DRAFT",
                true,
                2L,
                null,
                userId,
                userId,
                Instant.now(),
                Instant.now()
        );

        when(draftService.updateDraft(eq(context), eq(draftId), any(DraftUpdateRequest.class))).thenReturn(response);

        String json = """
                {
                  "expectedVersion": 1,
                  "accountId": "%s",
                  "eventType": "DIRECT_SALE_RECEIPT",
                  "effectiveDate": "2026-10-15",
                  "amountInput": "3000.00",
                  "description": "Updated draft"
                }
                """.formatted(accountId);

        mockMvc.perform(put("/api/finance/drafts/{id}", draftId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.amountInput").value("3000.00"));
    }

    @Test
    @DisplayName("PUT /api/finance/drafts/{id} returns 409 Conflict on version mismatch")
    void updateDraft_versionConflict() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.ACCOUNTANT);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST))).thenReturn(context);

        when(draftService.updateDraft(eq(context), eq(draftId), any()))
                .thenThrow(new DraftConflictException("Draft has been modified by another operation. Expected version 1 but was 2"));

        String json = """
                {
                  "expectedVersion": 1,
                  "amountInput": "3000.00"
                }
                """;

        mockMvc.perform(put("/api/finance/drafts/{id}", draftId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Draft has been modified by another operation. Expected version 1 but was 2"));
    }

    @Test
    @DisplayName("POST /api/finance/drafts/{id}/discard returns 200 OK for OWNER")
    void discardDraft_ownerSuccess() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST))).thenReturn(context);

        DraftDetailResponse response = new DraftDetailResponse(
                draftId,
                accountId,
                "DIRECT_SALE_RECEIPT",
                "2026-10-15",
                "100.00",
                "Discarded",
                "DISCARDED",
                false,
                1L,
                null,
                userId,
                userId,
                Instant.now(),
                Instant.now()
        );

        when(draftService.discardDraft(eq(context), eq(draftId), any(DraftDiscardRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/finance/drafts/{id}/discard", draftId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\": 0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISCARDED"));
    }

    @Test
    @DisplayName("POST /api/finance/drafts/{id}/record returns 201 Created for OWNER with valid Idempotency-Key")
    void recordDraft_ownerSuccess() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST))).thenReturn(context);

        UUID txId = UUID.randomUUID();
        DraftDetailResponse draftDetail = new DraftDetailResponse(
                draftId,
                accountId,
                "DIRECT_SALE_RECEIPT",
                "2026-10-15",
                "1500.00",
                "Sale",
                "RECORDED",
                false,
                1L,
                txId,
                userId,
                userId,
                Instant.now(),
                Instant.now()
        );

        TransactionDetailResponse txDetail = new TransactionDetailResponse(
                txId,
                businessId,
                accountId,
                "Cash Counter",
                "CASH",
                "DIRECT_SALE_RECEIPT",
                "INCREASE",
                "1500.00",
                "PKR",
                "1500.00",
                LocalDate.of(2026, 10, 15),
                "Sale",
                "51500.00",
                Collections.singletonList(new MovementDetailResponse(
                        UUID.randomUUID(),
                        accountId,
                        "INCREASE",
                        "1500.00",
                        "PKR",
                        LocalDate.of(2026, 10, 15),
                        "2026-10-15T12:00:00Z"
                )),
                userId,
                "2026-10-15T12:00:00Z"
        );

        DraftRecordResponse recordResponse = new DraftRecordResponse(draftDetail, txDetail);

        when(draftService.recordDraft(eq(context), eq(draftId), eq("RECORD-KEY-999"), any(DraftRecordRequest.class)))
                .thenReturn(recordResponse);

        String json = """
                {
                  "expectedVersion": 0,
                  "confirmedAmount": "1500.00"
                }
                """;

        mockMvc.perform(post("/api/finance/drafts/{id}/record", draftId)
                        .header("Idempotency-Key", "RECORD-KEY-999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.draft.status").value("RECORDED"))
                .andExpect(jsonPath("$.draft.recordedTransactionId").value(txId.toString()))
                .andExpect(jsonPath("$.transaction.id").value(txId.toString()))
                .andExpect(jsonPath("$.transaction.amount").value("1500.00"));
    }

    @Test
    @DisplayName("POST /api/finance/drafts/{id}/record returns 400 Bad Request when Idempotency-Key is missing")
    void recordDraft_missingIdempotencyKey() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.ACCOUNTANT);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST))).thenReturn(context);

        when(draftService.recordDraft(eq(context), eq(draftId), any(), any()))
                .thenThrow(new DraftValidationException("Idempotency-Key", "Missing required header: Idempotency-Key"));

        String json = """
                {
                  "expectedVersion": 0,
                  "confirmedAmount": "1500.00"
                }
                """;

        mockMvc.perform(post("/api/finance/drafts/{id}/record", draftId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['Idempotency-Key']").value("Missing required header: Idempotency-Key"));
    }

    @Test
    @DisplayName("POST /api/finance/drafts/{id}/record returns 409 Conflict when draft already recorded with different key")
    void recordDraft_conflict() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.ACCOUNTANT);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST))).thenReturn(context);

        when(draftService.recordDraft(eq(context), eq(draftId), eq("KEY-DIFF"), any()))
                .thenThrow(new DraftConflictException("Draft has already been recorded"));

        String json = """
                {
                  "expectedVersion": 0,
                  "confirmedAmount": "1500.00"
                }
                """;

        mockMvc.perform(post("/api/finance/drafts/{id}/record", draftId)
                        .header("Idempotency-Key", "KEY-DIFF")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Draft has already been recorded"));
    }

    @Test
    @DisplayName("POST /api/finance/drafts/{id}/record returns 409 Conflict when same key is used with changed request payload")
    void recordDraft_duplicateResourcePayloadMismatch() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.ACCOUNTANT);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST))).thenReturn(context);

        when(draftService.recordDraft(eq(context), eq(draftId), eq("SAME-KEY"), any()))
                .thenThrow(new DuplicateResourceException("Idempotency key 'SAME-KEY' has already been used with a different request payload"));

        String json = """
                {
                  "version": 0,
                  "confirmedAmount": "16.55"
                }
                """;

        mockMvc.perform(post("/api/finance/drafts/{id}/record", draftId)
                        .header("Idempotency-Key", "SAME-KEY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Idempotency key 'SAME-KEY' has already been used with a different request payload"));
    }

    @Test
    @DisplayName("POST /api/finance/drafts/{id}/record returns 400 Bad Request on malformed confirmedAmount")
    void recordDraft_malformedConfirmedAmount() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.ACCOUNTANT);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST))).thenReturn(context);

        when(draftService.recordDraft(eq(context), eq(draftId), eq("KEY-SYNTAX"), any()))
                .thenThrow(new DraftValidationException("confirmedAmount", "Amount must be a positive decimal string with exactly two decimal places (e.g. '1500.56')"));

        String json = """
                {
                  "version": 0,
                  "confirmedAmount": " 15.55 "
                }
                """;

        mockMvc.perform(post("/api/finance/drafts/{id}/record", draftId)
                        .header("Idempotency-Key", "KEY-SYNTAX")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.confirmedAmount").value("Amount must be a positive decimal string with exactly two decimal places (e.g. '1500.56')"));
    }
}
