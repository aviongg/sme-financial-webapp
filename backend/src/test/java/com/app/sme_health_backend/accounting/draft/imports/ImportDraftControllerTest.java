package com.app.sme_health_backend.accounting.draft.imports;

import com.app.sme_health_backend.accounting.draft.DraftConflictException;
import com.app.sme_health_backend.accounting.draft.DraftValidationException;
import com.app.sme_health_backend.accounting.draft.imports.dto.ImportBatchDetailResponse;
import com.app.sme_health_backend.accounting.draft.imports.dto.ImportBatchListResponse;
import com.app.sme_health_backend.accounting.draft.imports.dto.ImportDraftRowItem;
import com.app.sme_health_backend.accounting.draft.imports.dto.ImportDraftRowResult;
import com.app.sme_health_backend.accounting.draft.imports.dto.ImportDraftsRequest;
import com.app.sme_health_backend.accounting.draft.imports.dto.ImportDraftsResponse;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.shared.exception.GlobalExceptionHandler;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ImportDraftController.class)
@Import(GlobalExceptionHandler.class)
class ImportDraftControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ImportDraftService importDraftService;

    @MockitoBean
    private BusinessAuthorizationService authService;

    private UUID businessId;
    private UUID userId;
    private UUID batchId;
    private UUID draftId;

    @BeforeEach
    void setUp() {
        businessId = UUID.randomUUID();
        userId = UUID.randomUUID();
        batchId = UUID.randomUUID();
        draftId = UUID.randomUUID();
    }

    @Test
    @DisplayName("POST /api/finance/drafts/import returns 201 Created for OWNER")
    void saveImportDrafts_ownerSuccess() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST))).thenReturn(context);

        ImportDraftRowResult rowResult = new ImportDraftRowResult(
                "row-1", 1, "REF-101", draftId, "SAVED", "DRAFT", true, 0L, null
        );
        ImportDraftsResponse response = new ImportDraftsResponse(
                batchId, "sales.csv", 1, 0, 1, List.of(rowResult)
        );

        when(importDraftService.saveImportDrafts(eq(context), eq("IDEM-IMPORT-1"), any(ImportDraftsRequest.class)))
                .thenReturn(response);

        String json = """
                {
                  "batchId": "%s",
                  "sourceLabel": "sales.csv",
                  "rows": [
                    {
                      "sourceRowId": "row-1",
                      "sourceRowNumber": 1,
                      "amountInput": "1500",
                      "description": "Retail Sale",
                      "sourceReference": "REF-101"
                    }
                  ]
                }
                """.formatted(batchId);

        mockMvc.perform(post("/api/finance/drafts/import")
                        .header("Idempotency-Key", "IDEM-IMPORT-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.batchId").value(batchId.toString()))
                .andExpect(jsonPath("$.savedCount").value(1))
                .andExpect(jsonPath("$.replayedCount").value(0))
                .andExpect(jsonPath("$.rows[0].sourceRowId").value("row-1"))
                .andExpect(jsonPath("$.rows[0].draftId").value(draftId.toString()));
    }

    @Test
    @DisplayName("POST /api/finance/drafts/import returns 403 Forbidden for VIEWER")
    void saveImportDrafts_viewerForbidden() throws Exception {
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST)))
                .thenThrow(new AccessDeniedException("User does not have FINANCIAL_TRANSACTION_POST permission"));

        mockMvc.perform(post("/api/finance/drafts/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/finance/drafts/import returns 400 Bad Request on validation failure")
    void saveImportDrafts_validationFailure() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST))).thenReturn(context);

        when(importDraftService.saveImportDrafts(any(), any(), any()))
                .thenThrow(new DraftValidationException("effectiveDate", "effectiveDate must be in ISO format (YYYY-MM-DD)"));

        mockMvc.perform(post("/api/finance/drafts/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.effectiveDate").value("effectiveDate must be in ISO format (YYYY-MM-DD)"));
    }

    @Test
    @DisplayName("POST /api/finance/drafts/import returns 409 Conflict on changed replay content")
    void saveImportDrafts_conflictFailure() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_POST))).thenReturn(context);

        when(importDraftService.saveImportDrafts(any(), any(), any()))
                .thenThrow(new DraftConflictException("Row row-1 was previously saved with different content"));

        mockMvc.perform(post("/api/finance/drafts/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Row row-1 was previously saved with different content"));
    }

    @Test
    @DisplayName("GET /api/finance/drafts/import/{batchId} returns 200 OK for FINANCIAL_TRANSACTION_READ")
    void getImportBatch_success() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.VIEWER);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_READ))).thenReturn(context);

        ImportBatchDetailResponse detail = new ImportBatchDetailResponse(
                batchId, "bank.csv", 1, Instant.now(), Instant.now(), List.of()
        );
        when(importDraftService.getImportBatch(eq(context), eq(batchId))).thenReturn(detail);

        mockMvc.perform(get("/api/finance/drafts/import/" + batchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").value(batchId.toString()))
                .andExpect(jsonPath("$.sourceLabel").value("bank.csv"));
    }

    @Test
    @DisplayName("GET /api/finance/drafts/import returns 200 OK paginated list")
    void listImportBatches_success() throws Exception {
        BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.VIEWER);
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_TRANSACTION_READ))).thenReturn(context);

        ImportBatchListResponse listResponse = new ImportBatchListResponse(
                List.of(), 0, 20, 0, 0
        );
        when(importDraftService.listImportBatches(eq(context), any(Pageable.class))).thenReturn(listResponse);

        mockMvc.perform(get("/api/finance/drafts/import"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
