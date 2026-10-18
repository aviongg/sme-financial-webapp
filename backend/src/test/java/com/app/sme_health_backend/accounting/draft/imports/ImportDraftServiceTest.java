package com.app.sme_health_backend.accounting.draft.imports;

import com.app.sme_health_backend.accounting.draft.DraftConflictException;
import com.app.sme_health_backend.accounting.draft.DraftStatus;
import com.app.sme_health_backend.accounting.draft.DraftValidationException;
import com.app.sme_health_backend.accounting.draft.RecordDraft;
import com.app.sme_health_backend.accounting.draft.RecordDraftRepository;
import com.app.sme_health_backend.accounting.draft.RecordDraftService;
import com.app.sme_health_backend.accounting.draft.imports.dto.ImportBatchDetailResponse;
import com.app.sme_health_backend.accounting.draft.imports.dto.ImportDraftRowItem;
import com.app.sme_health_backend.accounting.draft.imports.dto.ImportDraftRowResult;
import com.app.sme_health_backend.accounting.draft.imports.dto.ImportDraftsRequest;
import com.app.sme_health_backend.accounting.draft.imports.dto.ImportDraftsResponse;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.entity.FinancialIdempotencyRecord;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.model.TransactionEventType;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.accounting.repository.FinancialIdempotencyRepository;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.shared.exception.DuplicateResourceException;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportDraftServiceTest {

    private ImportBatchRepository batchRepository;
    private ImportBatchRowRepository batchRowRepository;
    private RecordDraftRepository draftRepository;
    private FinancialAccountRepository accountRepository;
    private FinancialIdempotencyRepository idempotencyRepository;
    private ObjectMapper objectMapper;
    private ImportDraftService importDraftService;

    private final UUID businessId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);

    private FinancialAccount pkrAccount;

    @BeforeEach
    void setUp() {
        batchRepository = mock(ImportBatchRepository.class);
        batchRowRepository = mock(ImportBatchRowRepository.class);
        draftRepository = mock(RecordDraftRepository.class);
        accountRepository = mock(FinancialAccountRepository.class);
        idempotencyRepository = mock(FinancialIdempotencyRepository.class);
        objectMapper = new ObjectMapper();

        importDraftService = new ImportDraftService(
                batchRepository,
                batchRowRepository,
                draftRepository,
                accountRepository,
                idempotencyRepository,
                objectMapper
        );

        when(batchRepository.save(any(ImportBatch.class))).thenAnswer(inv -> inv.getArgument(0));
        when(batchRepository.saveAndFlush(any(ImportBatch.class))).thenAnswer(inv -> inv.getArgument(0));
        when(batchRowRepository.save(any(ImportBatchRow.class))).thenAnswer(inv -> inv.getArgument(0));
        when(batchRowRepository.countByBusinessIdAndBatchId(any(), any())).thenReturn(2);
        when(draftRepository.saveAndFlush(any(RecordDraft.class))).thenAnswer(inv -> {
            RecordDraft d = inv.getArgument(0);
            if (d.getId() == null) {
                d.setId(UUID.randomUUID());
            }
            return d;
        });

        pkrAccount = new FinancialAccount(
                businessId,
                "Main Cash",
                AccountType.CASH,
                "PKR",
                2,
                BigDecimal.ZERO,
                LocalDate.now(),
                AccountStatus.ACTIVE,
                "Bank",
                "ACC-1",
                "Cash drawer"
        );
    }

    @Test
    @DisplayName("Successfully saves valid reviewed spreadsheet rows as persistent drafts")
    void saveImportDrafts_success() {
        UUID batchId = UUID.randomUUID();
        when(accountRepository.findByIdAndBusinessId(accountId, businessId)).thenReturn(Optional.of(pkrAccount));
        when(batchRepository.findByIdAndBusinessId(batchId, businessId)).thenReturn(Optional.empty());

        ImportDraftRowItem row1 = new ImportDraftRowItem(
                "row-1",
                1,
                accountId,
                "DIRECT_SALE_RECEIPT",
                "2026-10-15",
                "5000.50",
                "Cash Sale #101",
                "INV-101"
        );
        ImportDraftRowItem row2 = new ImportDraftRowItem(
                "row-2",
                2,
                null,
                null,
                null,
                "1200",
                "Miscellaneous",
                null
        );

        ImportDraftsRequest request = new ImportDraftsRequest(
                batchId,
                "bank_october.csv",
                List.of(row1, row2)
        );

        ImportDraftsResponse response = importDraftService.saveImportDrafts(context, null, request);

        assertThat(response.batchId()).isEqualTo(batchId);
        assertThat(response.sourceLabel()).isEqualTo("bank_october.csv");
        assertThat(response.savedCount()).isEqualTo(2);
        assertThat(response.replayedCount()).isEqualTo(0);
        assertThat(response.totalRowsInBatch()).isEqualTo(2);
        assertThat(response.rows()).hasSize(2);

        ImportDraftRowResult res1 = response.rows().get(0);
        assertThat(res1.sourceRowId()).isEqualTo("row-1");
        assertThat(res1.sourceRowNumber()).isEqualTo(1);
        assertThat(res1.sourceReference()).isEqualTo("INV-101");
        assertThat(res1.status()).isEqualTo("SAVED");
        assertThat(res1.draftStatus()).isEqualTo("DRAFT");
        assertThat(res1.readyToRecord()).isTrue();

        ImportDraftRowResult res2 = response.rows().get(1);
        assertThat(res2.sourceRowId()).isEqualTo("row-2");
        assertThat(res2.sourceRowNumber()).isEqualTo(2);
        assertThat(res2.status()).isEqualTo("SAVED");

        ArgumentCaptor<RecordDraft> draftCaptor = ArgumentCaptor.forClass(RecordDraft.class);
        verify(draftRepository, times(2)).saveAndFlush(draftCaptor.capture());

        List<RecordDraft> capturedDrafts = draftCaptor.getAllValues();
        RecordDraft d1 = capturedDrafts.get(0);
        assertThat(d1.getAmountInput()).isEqualTo("5000.50");
        assertThat(d1.getEffectiveDate()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(d1.getEventType()).isEqualTo(TransactionEventType.DIRECT_SALE_RECEIPT);
        assertThat(d1.getAccountId()).isEqualTo(accountId);

        RecordDraft d2 = capturedDrafts.get(1);
        assertThat(d2.getAmountInput()).isEqualTo("1200");
        assertThat(d2.getEffectiveDate()).isNull();
        assertThat(d2.getEventType()).isNull(); // Incomplete row missing classification remains null; never inferred
        assertThat(d2.getAccountId()).isNull();
    }

    @Test
    @DisplayName("Rejects request exceeding 100 rows limit")
    void saveImportDrafts_exceedsRowLimit() {
        List<ImportDraftRowItem> rows = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            rows.add(new ImportDraftRowItem("r-" + i, i + 1, null, null, null, "100", "test", null));
        }
        ImportDraftsRequest request = new ImportDraftsRequest(UUID.randomUUID(), "test.csv", rows);

        assertThatThrownBy(() -> importDraftService.saveImportDrafts(context, null, request))
                .isInstanceOf(DraftValidationException.class)
                .hasMessageContaining("exceeds maximum allowed limit of 100");
    }

    @Test
    @DisplayName("Rejects empty request or empty rows")
    void saveImportDrafts_emptyRows() {
        ImportDraftsRequest request = new ImportDraftsRequest(UUID.randomUUID(), "test.csv", List.of());

        assertThatThrownBy(() -> importDraftService.saveImportDrafts(context, null, request))
                .isInstanceOf(DraftValidationException.class)
                .hasMessageContaining("must contain at least one row");
    }

    @Test
    @DisplayName("Rejects duplicate source row IDs in same request")
    void saveImportDrafts_duplicateRowIdsInRequest() {
        ImportDraftRowItem row1 = new ImportDraftRowItem("row-dup", 1, null, null, null, "100", "A", null);
        ImportDraftRowItem row2 = new ImportDraftRowItem("row-dup", 2, null, null, null, "200", "B", null);
        ImportDraftsRequest request = new ImportDraftsRequest(UUID.randomUUID(), "test.csv", List.of(row1, row2));

        assertThatThrownBy(() -> importDraftService.saveImportDrafts(context, null, request))
                .isInstanceOf(DraftValidationException.class)
                .hasMessageContaining("Duplicate sourceRowId in request: row-dup");
    }

    @Test
    @DisplayName("Rejects non-PKR currency account")
    void saveImportDrafts_nonPkrAccountRejected() {
        FinancialAccount usdAccount = new FinancialAccount(
                businessId,
                "USD Account",
                AccountType.BANK,
                "USD",
                2,
                BigDecimal.ZERO,
                LocalDate.now(),
                AccountStatus.ACTIVE,
                "Bank",
                "ACC-2",
                "USD"
        );
        when(accountRepository.findByIdAndBusinessId(accountId, businessId)).thenReturn(Optional.of(usdAccount));

        ImportDraftRowItem row = new ImportDraftRowItem("r1", 1, accountId, null, null, "100", "desc", null);
        ImportDraftsRequest request = new ImportDraftsRequest(UUID.randomUUID(), "test.csv", List.of(row));

        assertThatThrownBy(() -> importDraftService.saveImportDrafts(context, null, request))
                .isInstanceOf(DraftValidationException.class)
                .hasMessageContaining("Account USD Account has currency USD; only PKR accounts are supported");
    }

    @Test
    @DisplayName("Rejects inactive account")
    void saveImportDrafts_inactiveAccountRejected() {
        pkrAccount.setStatus(AccountStatus.INACTIVE);
        when(accountRepository.findByIdAndBusinessId(accountId, businessId)).thenReturn(Optional.of(pkrAccount));

        ImportDraftRowItem row = new ImportDraftRowItem("r1", 1, accountId, null, null, "100", "desc", null);
        ImportDraftsRequest request = new ImportDraftsRequest(UUID.randomUUID(), "test.csv", List.of(row));

        assertThatThrownBy(() -> importDraftService.saveImportDrafts(context, null, request))
                .isInstanceOf(DraftValidationException.class)
                .hasMessageContaining("is not ACTIVE");
    }

    @Test
    @DisplayName("Rejects cross-business account reference with non-disclosing message")
    void saveImportDrafts_crossBusinessAccountRejected() {
        when(accountRepository.findByIdAndBusinessId(accountId, businessId)).thenReturn(Optional.empty());

        ImportDraftRowItem row = new ImportDraftRowItem("r1", 1, accountId, null, null, "100", "desc", null);
        ImportDraftsRequest request = new ImportDraftsRequest(UUID.randomUUID(), "test.csv", List.of(row));

        assertThatThrownBy(() -> importDraftService.saveImportDrafts(context, null, request))
                .isInstanceOf(DraftValidationException.class)
                .hasMessageContaining("Referenced account not found in active business");
    }

    @Test
    @DisplayName("Replay identical row returns existing draft status with REPLAYED action")
    void saveImportDrafts_replayIdenticalRow() {
        UUID batchId = UUID.randomUUID();
        UUID draftId = UUID.randomUUID();
        ImportBatch batch = new ImportBatch(batchId, businessId, "test.csv", userId);

        String payloadHash = importDraftService.computeRowPayloadHash(
                accountId, "DIRECT_SALE_RECEIPT", "2026-10-15", "5000", "Sale", "REF1"
        );
        ImportBatchRow existingRow = new ImportBatchRow(
                businessId, batchId, "row-1", 1, "REF1", "5000", payloadHash, draftId
        );

        RecordDraft existingDraft = new RecordDraft(
                businessId, accountId, TransactionEventType.DIRECT_SALE_RECEIPT,
                LocalDate.of(2026, 10, 15), "5000", "Sale", userId
        );
        existingDraft.setId(draftId);
        existingDraft.setStatus(DraftStatus.DRAFT);

        when(accountRepository.findByIdAndBusinessId(accountId, businessId)).thenReturn(Optional.of(pkrAccount));
        when(batchRepository.findByIdAndBusinessId(batchId, businessId)).thenReturn(Optional.of(batch));
        when(batchRowRepository.findByBusinessIdAndBatchIdAndSourceRowIdIn(eq(businessId), eq(batchId), any()))
                .thenReturn(List.of(existingRow));
        when(batchRowRepository.countByBusinessIdAndBatchId(eq(businessId), eq(batchId))).thenReturn(1);
        when(draftRepository.findByIdAndBusinessId(draftId, businessId)).thenReturn(Optional.of(existingDraft));

        ImportDraftRowItem row = new ImportDraftRowItem(
                "row-1", 1, accountId, "DIRECT_SALE_RECEIPT", "2026-10-15", "5000", "Sale", "REF1"
        );
        ImportDraftsRequest request = new ImportDraftsRequest(batchId, "test.csv", List.of(row));

        ImportDraftsResponse response = importDraftService.saveImportDrafts(context, null, request);

        assertThat(response.savedCount()).isEqualTo(0);
        assertThat(response.replayedCount()).isEqualTo(1);
        assertThat(response.rows().get(0).status()).isEqualTo("REPLAYED");
        assertThat(response.rows().get(0).draftId()).isEqualTo(draftId);
        assertThat(response.rows().get(0).draftStatus()).isEqualTo("DRAFT");

        verify(draftRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Reusing source-row ID with changed content returns 409 Conflict")
    void saveImportDrafts_conflictOnChangedContent() {
        UUID batchId = UUID.randomUUID();
        UUID draftId = UUID.randomUUID();
        ImportBatch batch = new ImportBatch(batchId, businessId, "test.csv", userId);

        String originalHash = importDraftService.computeRowPayloadHash(
                accountId, "DIRECT_SALE_RECEIPT", "2026-10-15", "5000", "Sale", "REF1"
        );
        ImportBatchRow existingRow = new ImportBatchRow(
                businessId, batchId, "row-1", 1, "REF1", "5000", originalHash, draftId
        );

        when(accountRepository.findByIdAndBusinessId(accountId, businessId)).thenReturn(Optional.of(pkrAccount));
        when(batchRepository.findByIdAndBusinessId(batchId, businessId)).thenReturn(Optional.of(batch));
        when(batchRowRepository.findByBusinessIdAndBatchIdAndSourceRowIdIn(eq(businessId), eq(batchId), any()))
                .thenReturn(List.of(existingRow));

        // Request has modified amount "9999"
        ImportDraftRowItem modifiedRow = new ImportDraftRowItem(
                "row-1", 1, accountId, "DIRECT_SALE_RECEIPT", "2026-10-15", "9999", "Sale", "REF1"
        );
        ImportDraftsRequest request = new ImportDraftsRequest(batchId, "test.csv", List.of(modifiedRow));

        assertThatThrownBy(() -> importDraftService.saveImportDrafts(context, null, request))
                .isInstanceOf(DraftConflictException.class)
                .hasMessageContaining("Row row-1 was previously saved with different content");
    }

    @Test
    @DisplayName("Exact raw amount text survives verbatim without trimming or rounding")
    void saveImportDrafts_verbatimRawAmountSurvives() {
        UUID batchId = UUID.randomUUID();
        when(batchRepository.findByIdAndBusinessId(batchId, businessId)).thenReturn(Optional.empty());
        when(batchRepository.save(any(ImportBatch.class))).thenAnswer(inv -> inv.getArgument(0));

        when(draftRepository.saveAndFlush(any(RecordDraft.class))).thenAnswer(inv -> {
            RecordDraft d = inv.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });

        String verbatimAmount = "  001234.5000  ";
        ImportDraftRowItem row = new ImportDraftRowItem(
                "r-verbatim", 1, null, null, null, verbatimAmount, "raw check", null
        );
        ImportDraftsRequest request = new ImportDraftsRequest(batchId, "file.csv", List.of(row));

        importDraftService.saveImportDrafts(context, null, request);

        ArgumentCaptor<RecordDraft> draftCaptor = ArgumentCaptor.forClass(RecordDraft.class);
        verify(draftRepository).saveAndFlush(draftCaptor.capture());
        assertThat(draftCaptor.getValue().getAmountInput()).isEqualTo(verbatimAmount);

        ArgumentCaptor<ImportBatchRow> rowCaptor = ArgumentCaptor.forClass(ImportBatchRow.class);
        verify(batchRowRepository).save(rowCaptor.capture());
        assertThat(rowCaptor.getValue().getRawAmountInput()).isEqualTo(verbatimAmount);
    }

    @Test
    @DisplayName("Malformed date format is rejected with actionable error")
    void saveImportDrafts_malformedDateRejected() {
        ImportDraftRowItem row = new ImportDraftRowItem(
                "r1", 1, null, null, "15/10/2026", "100", "malformed date", null
        );
        ImportDraftsRequest request = new ImportDraftsRequest(UUID.randomUUID(), "test.csv", List.of(row));

        assertThatThrownBy(() -> importDraftService.saveImportDrafts(context, null, request))
                .isInstanceOf(DraftValidationException.class)
                .hasMessageContaining("effectiveDate must be in ISO format (YYYY-MM-DD)");
    }

    @Test
    @DisplayName("Get import batch details returns draft results for recovery")
    void getImportBatch_success() {
        UUID batchId = UUID.randomUUID();
        UUID draftId = UUID.randomUUID();
        ImportBatch batch = new ImportBatch(batchId, businessId, "september.csv", userId);
        batch.setTotalRowsImported(1);

        ImportBatchRow row = new ImportBatchRow(
                businessId, batchId, "row-1", 1, "REF-1", "2500", "hash", draftId
        );

        RecordDraft draft = new RecordDraft(
                businessId, null, null, null, "2500", "desc", userId
        );
        draft.setId(draftId);
        draft.setStatus(DraftStatus.RECORDED);
        UUID recordedTxId = UUID.randomUUID();
        draft.setRecordedTransactionId(recordedTxId);

        when(batchRepository.findByIdAndBusinessId(batchId, businessId)).thenReturn(Optional.of(batch));
        when(batchRowRepository.findByBusinessIdAndBatchIdOrderBySourceRowNumberAsc(businessId, batchId))
                .thenReturn(List.of(row));
        when(draftRepository.findByIdAndBusinessId(draftId, businessId)).thenReturn(Optional.of(draft));

        ImportBatchDetailResponse detail = importDraftService.getImportBatch(context, batchId);

        assertThat(detail.batchId()).isEqualTo(batchId);
        assertThat(detail.sourceLabel()).isEqualTo("september.csv");
        assertThat(detail.totalRows()).isEqualTo(1);
        assertThat(detail.rows()).hasSize(1);

        ImportDraftRowResult r = detail.rows().get(0);
        assertThat(r.sourceRowId()).isEqualTo("row-1");
        assertThat(r.draftId()).isEqualTo(draftId);
        assertThat(r.draftStatus()).isEqualTo("RECORDED");
        assertThat(r.recordedTransactionId()).isEqualTo(recordedTxId);
    }
}
