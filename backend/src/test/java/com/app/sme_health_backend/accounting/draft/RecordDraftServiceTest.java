package com.app.sme_health_backend.accounting.draft;

import com.app.sme_health_backend.accounting.dto.MovementDetailResponse;
import com.app.sme_health_backend.accounting.dto.TransactionDetailResponse;
import com.app.sme_health_backend.accounting.dto.TransactionPostRequest;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.entity.FinancialIdempotencyRecord;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.model.MovementDirection;
import com.app.sme_health_backend.accounting.model.TransactionEventType;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.accounting.repository.FinancialIdempotencyRepository;
import com.app.sme_health_backend.accounting.service.TransactionPostService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.shared.exception.DuplicateResourceException;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecordDraftServiceTest {

    private RecordDraftRepository draftRepository;
    private FinancialAccountRepository accountRepository;
    private TransactionPostService transactionPostService;
    private FinancialIdempotencyRepository idempotencyRepository;
    private ObjectMapper objectMapper;
    private RecordDraftService draftService;

    private final UUID businessId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);

    @BeforeEach
    void setUp() {
        draftRepository = mock(RecordDraftRepository.class);
        accountRepository = mock(FinancialAccountRepository.class);
        transactionPostService = mock(TransactionPostService.class);
        idempotencyRepository = mock(FinancialIdempotencyRepository.class);
        objectMapper = new ObjectMapper();

        draftService = new RecordDraftService(
                draftRepository,
                accountRepository,
                transactionPostService,
                idempotencyRepository,
                objectMapper
        );
    }

    @Nested
    @DisplayName("Draft Creation & Resumption")
    class CreationAndResumptionTests {

        @Test
        @DisplayName("Create incomplete draft with unfinished amount and missing account")
        void createIncompleteDraft() {
            DraftCreateRequest request = new DraftCreateRequest(
                    null,
                    null,
                    null,
                    "1500.",
                    "Draft notes"
            );

            when(draftRepository.saveAndFlush(any(RecordDraft.class))).thenAnswer(invocation -> {
                RecordDraft d = invocation.getArgument(0);
                d.setId(UUID.randomUUID());
                return d;
            });

            DraftDetailResponse response = draftService.createDraft(context, null, request);

            assertThat(response.id()).isNotNull();
            assertThat(response.accountId()).isNull();
            assertThat(response.eventType()).isNull();
            assertThat(response.effectiveDate()).isNull();
            assertThat(response.amountInput()).isEqualTo("1500.");
            assertThat(response.description()).isEqualTo("Draft notes");
            assertThat(response.status()).isEqualTo("DRAFT");
            assertThat(response.readyToRecord()).isFalse();
            assertThat(response.version()).isEqualTo(0L);
            assertThat(response.recordedTransactionId()).isNull();
            assertThat(response.createdById()).isEqualTo(userId);
        }

        @Test
        @DisplayName("Create draft with valid account reference succeeds")
        void createDraftWithValidAccount() {
            FinancialAccount account = new FinancialAccount(
                    businessId, "Bank Account", AccountType.BANK, "PKR", 2,
                    new BigDecimal("1000.00"), LocalDate.of(2026, 1, 1), AccountStatus.ACTIVE,
                    null, null, null
            );
            account.setId(accountId);
            when(accountRepository.findByIdAndBusinessId(accountId, businessId)).thenReturn(Optional.of(account));

            when(draftRepository.saveAndFlush(any(RecordDraft.class))).thenAnswer(invocation -> {
                RecordDraft d = invocation.getArgument(0);
                d.setId(UUID.randomUUID());
                return d;
            });

            DraftCreateRequest request = new DraftCreateRequest(
                    accountId,
                    "DIRECT_SALE_RECEIPT",
                    "2026-10-15",
                    "500.00",
                    "Sale invoice"
            );

            DraftDetailResponse response = draftService.createDraft(context, null, request);

            assertThat(response.accountId()).isEqualTo(accountId);
            assertThat(response.eventType()).isEqualTo("DIRECT_SALE_RECEIPT");
            assertThat(response.effectiveDate()).isEqualTo("2026-10-15");
            assertThat(response.amountInput()).isEqualTo("500.00");
            assertThat(response.readyToRecord()).isTrue();
        }

        @Test
        @DisplayName("Create draft rejects non-existent or foreign account reference")
        void createDraftRejectsForeignAccount() {
            UUID foreignAccountId = UUID.randomUUID();
            when(accountRepository.findByIdAndBusinessId(foreignAccountId, businessId)).thenReturn(Optional.empty());

            DraftCreateRequest request = new DraftCreateRequest(
                    foreignAccountId,
                    null,
                    null,
                    null,
                    null
            );

            assertThatThrownBy(() -> draftService.createDraft(context, null, request))
                    .isInstanceOf(DraftValidationException.class)
                    .hasMessageContaining("Validation Failed")
                    .satisfies(e -> assertThat(((DraftValidationException) e).getErrors()).containsKey("accountId"));
        }

        @Test
        @DisplayName("Create draft rejects excessive amountInput length without trimming")
        void createDraftRejectsExcessiveAmountLength() {
            String longAmount = "1".repeat(101);
            DraftCreateRequest request = new DraftCreateRequest(null, null, null, longAmount, null);

            assertThatThrownBy(() -> draftService.createDraft(context, null, request))
                    .isInstanceOf(DraftValidationException.class)
                    .satisfies(e -> assertThat(((DraftValidationException) e).getErrors()).containsKey("amountInput"));

            // Whitespace padding that causes length > 100 must also be rejected without trimming
            String paddedAmount = "   " + "1".repeat(98);
            DraftCreateRequest paddedReq = new DraftCreateRequest(null, null, null, paddedAmount, null);
            assertThatThrownBy(() -> draftService.createDraft(context, null, paddedReq))
                    .isInstanceOf(DraftValidationException.class)
                    .satisfies(e -> assertThat(((DraftValidationException) e).getErrors()).containsKey("amountInput"));
        }

        @Test
        @DisplayName("Create and update draft preserves verbatim amountInput with whitespace and natural language digits")
        void draftPreservesVerbatimAmountInput() {
            when(draftRepository.saveAndFlush(any(RecordDraft.class))).thenAnswer(invocation -> {
                RecordDraft d = invocation.getArgument(0);
                if (d.getId() == null) d.setId(UUID.randomUUID());
                return d;
            });

            // Create with leading/trailing whitespace
            String verbatimInput = " ۱۵۰۰. ";
            DraftCreateRequest createReq = new DraftCreateRequest(null, null, null, verbatimInput, "Verbatim test");
            DraftDetailResponse created = draftService.createDraft(context, null, createReq);
            assertThat(created.amountInput()).isEqualTo(verbatimInput);

            // Update with whitespace and unfinished punctuation
            RecordDraft existingDraft = new RecordDraft(businessId, null, null, null, verbatimInput, "Verbatim test", userId);
            existingDraft.setId(created.id());
            existingDraft.setVersion(0L);
            when(draftRepository.findByIdAndBusinessId(created.id(), businessId)).thenReturn(Optional.of(existingDraft));

            String updatedVerbatim = "  ۱,۵۰۰.۵  ";
            DraftUpdateRequest updateReq = new DraftUpdateRequest(0L, null, null, null, updatedVerbatim, "Updated verbatim");
            DraftDetailResponse updated = draftService.updateDraft(context, created.id(), updateReq);
            assertThat(updated.amountInput()).isEqualTo(updatedVerbatim);
        }

        @Test
        @DisplayName("Create draft with Idempotency-Key returns cached response on replay")
        void createDraftIdempotency() throws Exception {
            String idempotencyKey = "DRAFT-KEY-1";
            DraftCreateRequest request = new DraftCreateRequest(null, null, null, "100.00", "Idempotent draft");

            DraftDetailResponse initialResponse = new DraftDetailResponse(
                    UUID.randomUUID(), null, null, null, "100.00", "Idempotent draft",
                    "DRAFT", false, 0L, null, userId, userId, Instant.now(), Instant.now()
            );

            FinancialIdempotencyRecord record = new FinancialIdempotencyRecord(
                    businessId, "RECORD_DRAFT_CREATE", idempotencyKey,
                    "dummyHash", initialResponse.id(), 201, objectMapper.writeValueAsString(initialResponse)
            );

            // Mock calculating payload hash
            when(idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(businessId, "RECORD_DRAFT_CREATE", idempotencyKey))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(record));

            when(draftRepository.saveAndFlush(any(RecordDraft.class))).thenAnswer(invocation -> {
                RecordDraft d = invocation.getArgument(0);
                d.setId(initialResponse.id());
                return d;
            });

            // First call
            DraftDetailResponse first = draftService.createDraft(context, idempotencyKey, request);
            assertThat(first.id()).isEqualTo(initialResponse.id());

            // Replay call with altered payload throws DuplicateResourceException
            when(idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(businessId, "RECORD_DRAFT_CREATE", idempotencyKey))
                    .thenReturn(Optional.of(record));

            DraftCreateRequest alteredRequest = new DraftCreateRequest(null, null, null, "200.00", "Altered");
            assertThatThrownBy(() -> draftService.createDraft(context, idempotencyKey, alteredRequest))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("used with a different request payload");
        }
    }

    @Nested
    @DisplayName("Draft Updates & Optimistic Concurrency")
    class UpdateTests {

        @Test
        @DisplayName("Update draft succeeds with matching version")
        void updateDraftMatchingVersion() {
            UUID draftId = UUID.randomUUID();
            RecordDraft draft = new RecordDraft(businessId, null, null, null, "100", null, userId);
            draft.setId(draftId);
            draft.setVersion(0L);

            when(draftRepository.findByIdAndBusinessId(draftId, businessId)).thenReturn(Optional.of(draft));
            when(draftRepository.saveAndFlush(any(RecordDraft.class))).thenAnswer(i -> i.getArgument(0));

            DraftUpdateRequest updateReq = new DraftUpdateRequest(
                    0L,
                    null,
                    "OPERATING_EXPENSE_PAYMENT",
                    "2026-10-15",
                    "250.00",
                    "Office supplies"
            );

            DraftDetailResponse response = draftService.updateDraft(context, draftId, updateReq);

            assertThat(response.amountInput()).isEqualTo("250.00");
            assertThat(response.eventType()).isEqualTo("OPERATING_EXPENSE_PAYMENT");
            assertThat(response.effectiveDate()).isEqualTo("2026-10-15");
            assertThat(response.description()).isEqualTo("Office supplies");
        }

        @Test
        @DisplayName("Update draft with stale version throws DraftConflictException")
        void updateDraftStaleVersionThrowsConflict() {
            UUID draftId = UUID.randomUUID();
            RecordDraft draft = new RecordDraft(businessId, null, null, null, "100", null, userId);
            draft.setId(draftId);
            draft.setVersion(2L);

            when(draftRepository.findByIdAndBusinessId(draftId, businessId)).thenReturn(Optional.of(draft));

            DraftUpdateRequest staleReq = new DraftUpdateRequest(
                    1L, // stale version
                    null, null, null, "500", null
            );

            assertThatThrownBy(() -> draftService.updateDraft(context, draftId, staleReq))
                    .isInstanceOf(DraftConflictException.class)
                    .hasMessageContaining("Draft version mismatch: expected 2 but received 1");

            verify(draftRepository, never()).save(any());
        }

        @Test
        @DisplayName("Update draft in RECORDED or DISCARDED status throws DraftConflictException")
        void updateTerminalDraftThrowsConflict() {
            UUID draftId = UUID.randomUUID();
            RecordDraft draft = new RecordDraft(businessId, null, null, null, "100", null, userId);
            draft.setId(draftId);
            draft.setStatus(DraftStatus.RECORDED);

            when(draftRepository.findByIdAndBusinessId(draftId, businessId)).thenReturn(Optional.of(draft));

            DraftUpdateRequest req = new DraftUpdateRequest(0L, null, null, null, "200", null);

            assertThatThrownBy(() -> draftService.updateDraft(context, draftId, req))
                    .isInstanceOf(DraftConflictException.class)
                    .hasMessageContaining("Cannot update draft in RECORDED status");
        }
    }

    @Nested
    @DisplayName("Draft Discard Lifecycle")
    class DiscardTests {

        @Test
        @DisplayName("Discard draft marks status DISCARDED and preserves draft record")
        void discardDraft() {
            UUID draftId = UUID.randomUUID();
            RecordDraft draft = new RecordDraft(businessId, null, null, null, "100", null, userId);
            draft.setId(draftId);
            draft.setVersion(0L);

            when(draftRepository.findByIdAndBusinessId(draftId, businessId)).thenReturn(Optional.of(draft));
            when(draftRepository.saveAndFlush(any(RecordDraft.class))).thenAnswer(i -> i.getArgument(0));

            DraftDiscardRequest discardReq = new DraftDiscardRequest(0L, "Duplicate draft");
            DraftDetailResponse response = draftService.discardDraft(context, draftId, discardReq);

            assertThat(response.status()).isEqualTo("DISCARDED");
            assertThat(draft.getStatus()).isEqualTo(DraftStatus.DISCARDED);
        }

        @Test
        @DisplayName("Discarding with version mismatch throws DraftConflictException")
        void discardVersionMismatch() {
            UUID draftId = UUID.randomUUID();
            RecordDraft draft = new RecordDraft(businessId, null, null, null, "100", null, userId);
            draft.setId(draftId);
            draft.setVersion(1L);

            when(draftRepository.findByIdAndBusinessId(draftId, businessId)).thenReturn(Optional.of(draft));

            DraftDiscardRequest discardReq = new DraftDiscardRequest(0L, "Mismatch");
            assertThatThrownBy(() -> draftService.discardDraft(context, draftId, discardReq))
                    .isInstanceOf(DraftConflictException.class)
                    .hasMessageContaining("Draft version mismatch");
        }
    }

    @Nested
    @DisplayName("Draft Recording & Atomic Posting")
    class RecordTests {

        @Test
        @DisplayName("Successful recording delegates to TransactionPostService and marks draft RECORDED")
        void successfulRecording() {
            UUID draftId = UUID.randomUUID();
            RecordDraft draft = new RecordDraft(
                    businessId, accountId, TransactionEventType.DIRECT_SALE_RECEIPT,
                    LocalDate.of(2026, 10, 15), "1500.56", "Direct sale", userId
            );
            draft.setId(draftId);
            draft.setVersion(0L);

            when(draftRepository.findForUpdateByIdAndBusinessId(draftId, businessId)).thenReturn(Optional.of(draft));
            when(draftRepository.saveAndFlush(any(RecordDraft.class))).thenAnswer(i -> i.getArgument(0));

            UUID txId = UUID.randomUUID();
            TransactionDetailResponse txDetail = new TransactionDetailResponse(
                    txId,
                    businessId,
                    accountId,
                    "Bank Account",
                    "BANK",
                    "DIRECT_SALE_RECEIPT",
                    "INCREASE",
                    "1500.56",
                    "PKR",
                    "1500.56",
                    LocalDate.of(2026, 10, 15),
                    "Direct sale",
                    "101500.56",
                    Collections.singletonList(new MovementDetailResponse(
                            UUID.randomUUID(),
                            accountId,
                            "INCREASE",
                            "1500.56",
                            "PKR",
                            LocalDate.of(2026, 10, 15),
                            "2026-10-15T12:00:00Z"
                    )),
                    userId,
                    "2026-10-15T12:00:00Z"
            );

            when(transactionPostService.postTransaction(eq(context), eq("RECORD-KEY-1"), any(TransactionPostRequest.class)))
                    .thenReturn(txDetail);

            DraftRecordRequest recordReq = new DraftRecordRequest(
                    0L,
                    "1500.56",
                    null,
                    null,
                    null,
                    null
            );

            DraftRecordResponse result = draftService.recordDraft(context, draftId, "RECORD-KEY-1", recordReq);

            assertThat(result.draft().status()).isEqualTo("RECORDED");
            assertThat(result.draft().recordedTransactionId()).isEqualTo(txId);
            assertThat(result.transaction().id()).isEqualTo(txId);

            ArgumentCaptor<TransactionPostRequest> captor = ArgumentCaptor.forClass(TransactionPostRequest.class);
            verify(transactionPostService).postTransaction(eq(context), eq("RECORD-KEY-1"), captor.capture());
            TransactionPostRequest captured = captor.getValue();
            assertThat(captured.amount()).isEqualTo("1500.56");
            assertThat(captured.accountId()).isEqualTo(accountId);
            assertThat(captured.eventType()).isEqualTo("DIRECT_SALE_RECEIPT");
        }

        @Test
        @DisplayName("Recording draft with missing required fields fails validation and preserves draft in DRAFT state")
        void recordingMissingFieldsFailsValidation() {
            UUID draftId = UUID.randomUUID();
            RecordDraft draft = new RecordDraft(businessId, null, null, null, "100.00", null, userId);
            draft.setId(draftId);
            draft.setVersion(0L);

            when(draftRepository.findForUpdateByIdAndBusinessId(draftId, businessId)).thenReturn(Optional.of(draft));

            // Request does not provide missing accountId and eventType
            DraftRecordRequest recordReq = new DraftRecordRequest(
                    0L,
                    "100.00",
                    null,
                    null,
                    null,
                    null
            );

            assertThatThrownBy(() -> draftService.recordDraft(context, draftId, "KEY-1", recordReq))
                    .isInstanceOf(DraftValidationException.class)
                    .satisfies(e -> {
                        Map<String, String> errors = ((DraftValidationException) e).getErrors();
                        assertThat(errors).containsKey("accountId");
                        assertThat(errors).containsKey("eventType");
                        assertThat(errors).containsKey("effectiveDate");
                    });

            assertThat(draft.getStatus()).isEqualTo(DraftStatus.DRAFT);
            verify(transactionPostService, never()).postTransaction(any(), any(), any());
        }

        @Test
        @DisplayName("Failed posting preserves draft in DRAFT status")
        void failedPostingPreservesDraft() {
            UUID draftId = UUID.randomUUID();
            RecordDraft draft = new RecordDraft(
                    businessId, accountId, TransactionEventType.OPERATING_EXPENSE_PAYMENT,
                    LocalDate.of(2026, 10, 15), "5000.00", "Expense", userId
            );
            draft.setId(draftId);
            draft.setVersion(0L);

            when(draftRepository.findForUpdateByIdAndBusinessId(draftId, businessId)).thenReturn(Optional.of(draft));

            when(transactionPostService.postTransaction(any(), any(), any()))
                    .thenThrow(new IllegalArgumentException("Insufficient funds on cash account"));

            DraftRecordRequest recordReq = new DraftRecordRequest(0L, "5000.00", null, null, null, null);

            assertThatThrownBy(() -> draftService.recordDraft(context, draftId, "KEY-FAIL", recordReq))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Insufficient funds");

            assertThat(draft.getStatus()).isEqualTo(DraftStatus.DRAFT);
            assertThat(draft.getRecordedTransactionId()).isNull();
        }

        @Test
        @DisplayName("Concurrent recording attempt with different key on already-recorded draft throws DraftConflictException")
        void recordingAlreadyRecordedDraftThrowsConflict() {
            UUID draftId = UUID.randomUUID();
            UUID existingTxId = UUID.randomUUID();
            RecordDraft draft = new RecordDraft(businessId, accountId, TransactionEventType.DIRECT_SALE_RECEIPT, LocalDate.of(2026, 10, 15), "100.00", null, userId);
            draft.setId(draftId);
            draft.setStatus(DraftStatus.RECORDED);
            draft.setRecordedTransactionId(existingTxId);

            when(draftRepository.findForUpdateByIdAndBusinessId(draftId, businessId)).thenReturn(Optional.of(draft));
            when(idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(businessId, "FINANCIAL_TRANSACTION", "KEY-DIFFERENT"))
                    .thenReturn(Optional.empty());

            DraftRecordRequest recordReq = new DraftRecordRequest(0L, "100.00", null, null, null, null);

            assertThatThrownBy(() -> draftService.recordDraft(context, draftId, "KEY-DIFFERENT", recordReq))
                    .isInstanceOf(DraftConflictException.class)
                    .hasMessageContaining("already been recorded");

            verify(transactionPostService, never()).postTransaction(any(), any(), any());
        }

        @Test
        @DisplayName("Safe idempotent recovery returns cached transaction detail on repeat request")
        void safeIdempotentRecovery() throws Exception {
            UUID draftId = UUID.randomUUID();
            UUID existingTxId = UUID.randomUUID();
            RecordDraft draft = new RecordDraft(businessId, accountId, TransactionEventType.DIRECT_SALE_RECEIPT, LocalDate.of(2026, 10, 15), "100.00", null, userId);
            draft.setId(draftId);
            draft.setStatus(DraftStatus.RECORDED);
            draft.setRecordedTransactionId(existingTxId);
            draft.setVersion(1L);

            when(draftRepository.findForUpdateByIdAndBusinessId(draftId, businessId)).thenReturn(Optional.of(draft));

            TransactionDetailResponse cachedTx = new TransactionDetailResponse(
                    existingTxId,
                    businessId,
                    accountId,
                    "Bank Account",
                    "BANK",
                    "DIRECT_SALE_RECEIPT",
                    "INCREASE",
                    "100.00",
                    "PKR",
                    "100.00",
                    LocalDate.of(2026, 10, 15),
                    null,
                    "10100.00",
                    Collections.emptyList(),
                    userId,
                    "2026-10-15T12:00:00Z"
            );

            String expectedHash = TransactionPostService.computePayloadHash(
                    accountId,
                    TransactionEventType.DIRECT_SALE_RECEIPT,
                    new BigDecimal("100.00"),
                    LocalDate.of(2026, 10, 15),
                    null
            );

            FinancialIdempotencyRecord record = new FinancialIdempotencyRecord(
                    businessId, "FINANCIAL_TRANSACTION", "ORIGINAL-KEY",
                    expectedHash, existingTxId, 201, objectMapper.writeValueAsString(cachedTx)
            );

            when(idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(businessId, "FINANCIAL_TRANSACTION", "ORIGINAL-KEY"))
                    .thenReturn(Optional.of(record));

            DraftRecordRequest recordReq = new DraftRecordRequest(0L, "100.00", null, null, null, null);

            DraftRecordResponse result = draftService.recordDraft(context, draftId, "ORIGINAL-KEY", recordReq);

            assertThat(result.draft().status()).isEqualTo("RECORDED");
            assertThat(result.transaction().id()).isEqualTo(existingTxId);
            verify(transactionPostService, never()).postTransaction(any(), any(), any());
        }

        @Test
        @DisplayName("Replay with same key but changed confirmedAmount throws DuplicateResourceException (409 Conflict)")
        void replayChangedConfirmedAmountThrowsConflict() throws Exception {
            UUID draftId = UUID.randomUUID();
            UUID existingTxId = UUID.randomUUID();
            RecordDraft draft = new RecordDraft(businessId, accountId, TransactionEventType.DIRECT_SALE_RECEIPT, LocalDate.of(2026, 10, 15), "100.00", null, userId);
            draft.setId(draftId);
            draft.setStatus(DraftStatus.RECORDED);
            draft.setRecordedTransactionId(existingTxId);
            draft.setVersion(1L);

            when(draftRepository.findForUpdateByIdAndBusinessId(draftId, businessId)).thenReturn(Optional.of(draft));

            TransactionDetailResponse cachedTx = new TransactionDetailResponse(
                    existingTxId, businessId, accountId, "Bank Account", "BANK",
                    "DIRECT_SALE_RECEIPT", "INCREASE", "100.00", "PKR", "100.00",
                    LocalDate.of(2026, 10, 15), null, "10100.00", Collections.emptyList(),
                    userId, "2026-10-15T12:00:00Z"
            );

            String originalHash = TransactionPostService.computePayloadHash(
                    accountId, TransactionEventType.DIRECT_SALE_RECEIPT, new BigDecimal("100.00"),
                    LocalDate.of(2026, 10, 15), null
            );

            FinancialIdempotencyRecord record = new FinancialIdempotencyRecord(
                    businessId, "FINANCIAL_TRANSACTION", "ORIGINAL-KEY",
                    originalHash, existingTxId, 201, objectMapper.writeValueAsString(cachedTx)
            );

            when(idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(businessId, "FINANCIAL_TRANSACTION", "ORIGINAL-KEY"))
                    .thenReturn(Optional.of(record));

            // Changed confirmed amount: 101.00 instead of 100.00
            DraftRecordRequest changedReq = new DraftRecordRequest(0L, "101.00", null, null, null, null);

            assertThatThrownBy(() -> draftService.recordDraft(context, draftId, "ORIGINAL-KEY", changedReq))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("different request payload");

            verify(transactionPostService, never()).postTransaction(any(), any(), any());
        }

        @Test
        @DisplayName("Replay with same key but changed version throws DuplicateResourceException (409 Conflict)")
        void replayChangedVersionThrowsConflict() throws Exception {
            UUID draftId = UUID.randomUUID();
            UUID existingTxId = UUID.randomUUID();
            RecordDraft draft = new RecordDraft(businessId, accountId, TransactionEventType.DIRECT_SALE_RECEIPT, LocalDate.of(2026, 10, 15), "100.00", null, userId);
            draft.setId(draftId);
            draft.setStatus(DraftStatus.RECORDED);
            draft.setRecordedTransactionId(existingTxId);
            draft.setVersion(1L);

            when(draftRepository.findForUpdateByIdAndBusinessId(draftId, businessId)).thenReturn(Optional.of(draft));

            TransactionDetailResponse cachedTx = new TransactionDetailResponse(
                    existingTxId, businessId, accountId, "Bank Account", "BANK",
                    "DIRECT_SALE_RECEIPT", "INCREASE", "100.00", "PKR", "100.00",
                    LocalDate.of(2026, 10, 15), null, "10100.00", Collections.emptyList(),
                    userId, "2026-10-15T12:00:00Z"
            );

            String originalHash = TransactionPostService.computePayloadHash(
                    accountId, TransactionEventType.DIRECT_SALE_RECEIPT, new BigDecimal("100.00"),
                    LocalDate.of(2026, 10, 15), null
            );

            FinancialIdempotencyRecord record = new FinancialIdempotencyRecord(
                    businessId, "FINANCIAL_TRANSACTION", "ORIGINAL-KEY",
                    originalHash, existingTxId, 201, objectMapper.writeValueAsString(cachedTx)
            );

            when(idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(businessId, "FINANCIAL_TRANSACTION", "ORIGINAL-KEY"))
                    .thenReturn(Optional.of(record));

            // Changed version: 1L instead of 0L
            DraftRecordRequest changedReq = new DraftRecordRequest(1L, "100.00", null, null, null, null);

            assertThatThrownBy(() -> draftService.recordDraft(context, draftId, "ORIGINAL-KEY", changedReq))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("different request payload");

            verify(transactionPostService, never()).postTransaction(any(), any(), any());
        }

        @Test
        @DisplayName("Replay with same key but changed description throws DuplicateResourceException (409 Conflict)")
        void replayChangedDescriptionThrowsConflict() throws Exception {
            UUID draftId = UUID.randomUUID();
            UUID existingTxId = UUID.randomUUID();
            RecordDraft draft = new RecordDraft(businessId, accountId, TransactionEventType.DIRECT_SALE_RECEIPT, LocalDate.of(2026, 10, 15), "100.00", "Original notes", userId);
            draft.setId(draftId);
            draft.setStatus(DraftStatus.RECORDED);
            draft.setRecordedTransactionId(existingTxId);
            draft.setVersion(1L);

            when(draftRepository.findForUpdateByIdAndBusinessId(draftId, businessId)).thenReturn(Optional.of(draft));

            TransactionDetailResponse cachedTx = new TransactionDetailResponse(
                    existingTxId, businessId, accountId, "Bank Account", "BANK",
                    "DIRECT_SALE_RECEIPT", "INCREASE", "100.00", "PKR", "100.00",
                    LocalDate.of(2026, 10, 15), "Original notes", "10100.00", Collections.emptyList(),
                    userId, "2026-10-15T12:00:00Z"
            );

            String originalHash = TransactionPostService.computePayloadHash(
                    accountId, TransactionEventType.DIRECT_SALE_RECEIPT, new BigDecimal("100.00"),
                    LocalDate.of(2026, 10, 15), "Original notes"
            );

            FinancialIdempotencyRecord record = new FinancialIdempotencyRecord(
                    businessId, "FINANCIAL_TRANSACTION", "ORIGINAL-KEY",
                    originalHash, existingTxId, 201, objectMapper.writeValueAsString(cachedTx)
            );

            when(idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(businessId, "FINANCIAL_TRANSACTION", "ORIGINAL-KEY"))
                    .thenReturn(Optional.of(record));

            // Changed description in request
            DraftRecordRequest changedReq = new DraftRecordRequest(0L, "100.00", null, null, null, "Changed notes");

            assertThatThrownBy(() -> draftService.recordDraft(context, draftId, "ORIGINAL-KEY", changedReq))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("different request payload");

            verify(transactionPostService, never()).postTransaction(any(), any(), any());
        }

        @Test
        @DisplayName("Unrecorded draft attempting to reuse existing transaction idempotency key throws DuplicateResourceException (409 Conflict)")
        void unrecordedDraftReusingKeyThrowsConflict() {
            UUID draftId = UUID.randomUUID();
            RecordDraft draft = new RecordDraft(businessId, accountId, TransactionEventType.DIRECT_SALE_RECEIPT, LocalDate.of(2026, 10, 15), "100.00", null, userId);
            draft.setId(draftId);
            draft.setVersion(0L);

            when(draftRepository.findForUpdateByIdAndBusinessId(draftId, businessId)).thenReturn(Optional.of(draft));

            FinancialIdempotencyRecord existingRecord = new FinancialIdempotencyRecord(
                    businessId, "FINANCIAL_TRANSACTION", "USED-KEY",
                    "some-hash", UUID.randomUUID(), 201, "{}"
            );
            when(idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(businessId, "FINANCIAL_TRANSACTION", "USED-KEY"))
                    .thenReturn(Optional.of(existingRecord));

            DraftRecordRequest recordReq = new DraftRecordRequest(0L, "100.00", null, null, null, null);

            assertThatThrownBy(() -> draftService.recordDraft(context, draftId, "USED-KEY", recordReq))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("different request payload");

            verify(transactionPostService, never()).postTransaction(any(), any(), any());
        }

        @ParameterizedTest(name = "First recording with malformed confirmedAmount \"{0}\" throws DraftValidationException")
        @ValueSource(strings = {
                " 15.55 ", "\t15.55\t", "15.55\n", "\u000015.55\u0000",
                "15.55 ", " 15.55", "\r\n15.55\r\n", "15\u0000.55", "15.5 5", "\f15.55", "\b15.55",
                "15.550", "1.555e1", "+15.55", "-15.55", "015.55", "15.5", "15", "15,55",
                "NaN", "Infinity", "۱۵.۵۵", "١٥.٥٥", "", "   ", "0.00", "1000000000000.00"
        })
        void firstRecordingMalformedConfirmedAmountThrowsValidationException(String invalidAmount) {
            UUID draftId = UUID.randomUUID();
            RecordDraft draft = new RecordDraft(
                    businessId, accountId, TransactionEventType.DIRECT_SALE_RECEIPT,
                    LocalDate.of(2026, 10, 15), "15.55", "Test", userId
            );
            draft.setId(draftId);
            draft.setVersion(0L);

            when(draftRepository.findForUpdateByIdAndBusinessId(draftId, businessId)).thenReturn(Optional.of(draft));

            DraftRecordRequest recordReq = new DraftRecordRequest(0L, invalidAmount, null, null, null, null);

            assertThatThrownBy(() -> draftService.recordDraft(context, draftId, "KEY-1", recordReq))
                    .isInstanceOf(DraftValidationException.class)
                    .satisfies(e -> {
                        Map<String, String> errors = ((DraftValidationException) e).getErrors();
                        assertThat(errors).containsKey("confirmedAmount");
                    });

            assertThat(draft.getStatus()).isEqualTo(DraftStatus.DRAFT);
            verify(transactionPostService, never()).postTransaction(any(), any(), any());
        }

        @ParameterizedTest(name = "Replay with malformed confirmedAmount \"{0}\" throws DraftValidationException (400) without cached return")
        @ValueSource(strings = {
                " 15.55 ", "\t15.55\t", "15.55\n", "\u000015.55\u0000",
                "15.55 ", " 15.55", "\r\n15.55\r\n", "15\u0000.55", "15.5 5", "\f15.55", "\b15.55",
                "15.550", "1.555e1", "+15.55", "-15.55", "015.55", "15.5", "15", "15,55",
                "NaN", "Infinity", "۱۵.۵۵", "١٥.٥٥", "", "   ", "0.00", "1000000000000.00"
        })
        void replayMalformedConfirmedAmountThrowsValidationException(String invalidAmount) {
            UUID draftId = UUID.randomUUID();
            UUID existingTxId = UUID.randomUUID();
            RecordDraft draft = new RecordDraft(
                    businessId, accountId, TransactionEventType.DIRECT_SALE_RECEIPT,
                    LocalDate.of(2026, 10, 15), "15.55", null, userId
            );
            draft.setId(draftId);
            draft.setStatus(DraftStatus.RECORDED);
            draft.setRecordedTransactionId(existingTxId);
            draft.setVersion(1L);

            DraftRecordRequest recordReq = new DraftRecordRequest(0L, invalidAmount, null, null, null, null);

            assertThatThrownBy(() -> draftService.recordDraft(context, draftId, "ORIGINAL-KEY", recordReq))
                    .isInstanceOf(DraftValidationException.class)
                    .satisfies(e -> {
                        Map<String, String> errors = ((DraftValidationException) e).getErrors();
                        assertThat(errors).containsKey("confirmedAmount");
                    });

            verify(transactionPostService, never()).postTransaction(any(), any(), any());
            verify(idempotencyRepository, never()).findByBusinessIdAndOperationAndIdempotencyKey(any(), any(), any());
        }
    }
}
