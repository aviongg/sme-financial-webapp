package com.app.sme_health_backend.obligation;

import com.app.sme_health_backend.accounting.entity.FinancialIdempotencyRecord;
import com.app.sme_health_backend.accounting.repository.FinancialIdempotencyRepository;
import com.app.sme_health_backend.audit.service.SecurityAuditService;
import com.app.sme_health_backend.commercial.model.CommercialDocumentKind;
import com.app.sme_health_backend.commercial.model.CommercialDocumentRecord;
import com.app.sme_health_backend.commercial.model.CommercialDocumentStatus;
import com.app.sme_health_backend.commercial.repository.CommercialDocumentRepository;
import com.app.sme_health_backend.counterparty.Counterparty;
import com.app.sme_health_backend.counterparty.CounterpartyRepository;
import com.app.sme_health_backend.counterparty.CounterpartyStatus;
import com.app.sme_health_backend.counterparty.CounterpartyType;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.obligation.aging.ObligationDirection;
import com.app.sme_health_backend.obligation.dto.*;
import com.app.sme_health_backend.shared.exception.DuplicateResourceException;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommercialRecognitionServiceTest {

    @Mock
    private FinancialObligationRepository obligationRepository;

    @Mock
    private ObligationPaymentAllocationRepository allocationRepository;

    @Mock
    private FinancialObligationService obligationService;

    @Mock
    private CounterpartyRepository counterpartyRepository;

    @Mock
    private CommercialDocumentRepository commercialDocumentRepository;

    @Mock
    private FinancialIdempotencyRepository idempotencyRepository;

    @Mock
    private SecurityAuditService securityAuditService;

    @Mock
    private JdbcTemplate jdbc;

    private ObjectMapper objectMapper;
    private CommercialRecognitionService service;

    private final UUID businessId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final BusinessAccessContext context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new CommercialRecognitionService(
                obligationRepository,
                allocationRepository,
                obligationService,
                counterpartyRepository,
                commercialDocumentRepository,
                idempotencyRepository,
                securityAuditService,
                jdbc,
                objectMapper
        );
    }

    @Test
    @DisplayName("Validation fails when request or mandatory fields are null")
    void testValidation_NullFields() {
        assertThatThrownBy(() -> service.recognizeCommercialDocument(context, "key-1", null, null))
                .isInstanceOf(ObligationValidationException.class)
                .hasMessageContaining("Request body is required");

        assertThatThrownBy(() -> service.recognizeCommercialDocument(context, "key-1", new CommercialRecognitionRequest(null, null, UUID.randomUUID(), "key", null), null))
                .isInstanceOf(ObligationValidationException.class)
                .hasMessageContaining("Document ID is required");

        assertThatThrownBy(() -> service.recognizeCommercialDocument(context, "key-1", new CommercialRecognitionRequest(UUID.randomUUID(), null, null, "key", null), null))
                .isInstanceOf(ObligationValidationException.class)
                .hasMessageContaining("Explicit counterparty selection is required");

        assertThatThrownBy(() -> service.recognizeCommercialDocument(context, "  ", new CommercialRecognitionRequest(UUID.randomUUID(), null, UUID.randomUUID(), null, null), null))
                .isInstanceOf(ObligationValidationException.class)
                .hasMessageContaining("Idempotency key is required");
    }

    @Test
    @DisplayName("Idempotent replay returns cached response when payload hash matches")
    void testIdempotency_ReplayCachedResponse() throws Exception {
        UUID docId = UUID.randomUUID();
        UUID cpId = UUID.randomUUID();
        String key = "idem-key-replay";

        CommercialRecognitionResponse cachedResp = new CommercialRecognitionResponse(
                UUID.randomUUID(), businessId, ObligationDirection.RECEIVABLE, ObligationSourceType.CUSTOMER_INVOICE,
                docId.toString(), "INV-000042", cpId, "Customer A", "PKR",
                new BigDecimal("115000.0000"), new BigDecimal("115000.0000"), BigDecimal.ZERO,
                ObligationStatus.OPEN, LocalDate.now(), null, null, "PROV", null, null
        );

        String json = objectMapper.writeValueAsString(cachedResp);

        // Compute matching hash
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = md.digest((businessId + "|" + docId + "|" + cpId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String matchingHash = HexFormat.of().formatHex(hashBytes);

        FinancialIdempotencyRecord existingRecord = new FinancialIdempotencyRecord(
                businessId, CommercialRecognitionService.OPERATION_COMMERCIAL_RECOGNITION, key, matchingHash, cachedResp.obligationId(), 201, json
        );

        when(idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(businessId, CommercialRecognitionService.OPERATION_COMMERCIAL_RECOGNITION, key))
                .thenReturn(Optional.of(existingRecord));

        CommercialRecognitionResponse actual = service.recognizeCommercialDocument(
                context, key, new CommercialRecognitionRequest(docId, CommercialDocumentKind.INVOICE, cpId, key, null), null
        );

        assertThat(actual.obligationId()).isEqualTo(cachedResp.obligationId());
        verifyNoInteractions(commercialDocumentRepository);
        verifyNoInteractions(obligationRepository);
    }

    @Test
    @DisplayName("Idempotency conflict thrown when same key used with different payload")
    void testIdempotency_ConflictOnDifferentPayload() {
        UUID docId = UUID.randomUUID();
        UUID cpId = UUID.randomUUID();
        String key = "idem-conflict";

        FinancialIdempotencyRecord existingRecord = new FinancialIdempotencyRecord(
                businessId, CommercialRecognitionService.OPERATION_COMMERCIAL_RECOGNITION, key, "different-hash", UUID.randomUUID(), 201, "{}"
        );

        when(idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(businessId, CommercialRecognitionService.OPERATION_COMMERCIAL_RECOGNITION, key))
                .thenReturn(Optional.of(existingRecord));

        assertThatThrownBy(() -> service.recognizeCommercialDocument(
                context, key, new CommercialRecognitionRequest(docId, CommercialDocumentKind.INVOICE, cpId, key, null), null
        ))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already been used with a different request payload");
    }

    @Test
    @DisplayName("Recognition fails when commercial document does not exist")
    void testRecognition_DocumentNotFound() {
        UUID docId = UUID.randomUUID();
        UUID cpId = UUID.randomUUID();
        String key = "doc-not-found";

        when(idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(jdbc.queryForList(anyString(), eq(docId), eq(businessId)))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> service.recognizeCommercialDocument(
                context, key, new CommercialRecognitionRequest(docId, CommercialDocumentKind.INVOICE, cpId, key, null), null
        ))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Commercial document not found");
    }

    @Test
    @DisplayName("Recognition fails when document is DRAFT or VOID")
    void testRecognition_InvalidStatus() {
        UUID docId = UUID.randomUUID();
        UUID cpId = UUID.randomUUID();
        String key = "status-check";

        when(idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(any(), any(), any()))
                .thenReturn(Optional.empty());

        // Draft
        when(jdbc.queryForList(anyString(), eq(docId), eq(businessId)))
                .thenReturn(List.of(Map.of("id", docId, "kind", "INVOICE", "status", "DRAFT")));

        assertThatThrownBy(() -> service.recognizeCommercialDocument(
                context, key, new CommercialRecognitionRequest(docId, CommercialDocumentKind.INVOICE, cpId, key, null), null
        ))
                .isInstanceOf(ObligationValidationException.class)
                .hasMessageContaining("Cannot recognize draft commercial document");

        // Void
        when(jdbc.queryForList(anyString(), eq(docId), eq(businessId)))
                .thenReturn(List.of(Map.of("id", docId, "kind", "INVOICE", "status", "VOID")));

        assertThatThrownBy(() -> service.recognizeCommercialDocument(
                context, key, new CommercialRecognitionRequest(docId, CommercialDocumentKind.INVOICE, cpId, key, null), null
        ))
                .isInstanceOf(ObligationValidationException.class)
                .hasMessageContaining("Cannot recognize voided commercial document");
    }

    @Test
    @DisplayName("Recognition fails when document is already recognized")
    void testRecognition_AlreadyRecognized() {
        UUID docId = UUID.randomUUID();
        UUID cpId = UUID.randomUUID();
        String key = "already-recog";

        when(idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(jdbc.queryForList(anyString(), eq(docId), eq(businessId)))
                .thenReturn(List.of(Map.of("id", docId, "kind", "INVOICE", "status", "FINALIZED")));

        CommercialDocumentRecord record = new CommercialDocumentRecord(
                docId, businessId, CommercialDocumentKind.INVOICE, CommercialDocumentStatus.FINALIZED,
                "INV-000042", "Customer Name", LocalDate.now(), LocalDate.now().plusDays(30),
                "Notes", new BigDecimal("100000.00"), new BigDecimal("100000.00"), "PKR",
                "NONE", "v1", 1L, userId, userId, userId, OffsetDateTime.now(), null, null, null,
                OffsetDateTime.now(), OffsetDateTime.now(), 1, Collections.emptyList()
        );
        when(commercialDocumentRepository.findWithLines(businessId, CommercialDocumentKind.INVOICE, docId))
                .thenReturn(Optional.of(record));

        FinancialObligation existingOb = new FinancialObligation();
        when(obligationRepository.findByBusinessIdAndSourceTypeAndSourceId(businessId, ObligationSourceType.CUSTOMER_INVOICE, docId.toString()))
                .thenReturn(Optional.of(existingOb));

        assertThatThrownBy(() -> service.recognizeCommercialDocument(
                context, key, new CommercialRecognitionRequest(docId, CommercialDocumentKind.INVOICE, cpId, key, null), null
        ))
                .isInstanceOf(ObligationConflictException.class)
                .hasMessageContaining("already recognized as a financial obligation");
    }

    @Test
    @DisplayName("Recognition fails when counterparty type is incompatible")
    void testRecognition_CounterpartyTypeIncompatible() {
        UUID docId = UUID.randomUUID();
        UUID cpId = UUID.randomUUID();
        String key = "cp-incompatible";

        when(idempotencyRepository.findByBusinessIdAndOperationAndIdempotencyKey(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(jdbc.queryForList(anyString(), eq(docId), eq(businessId)))
                .thenReturn(List.of(Map.of("id", docId, "kind", "INVOICE", "status", "FINALIZED")));

        CommercialDocumentRecord record = new CommercialDocumentRecord(
                docId, businessId, CommercialDocumentKind.INVOICE, CommercialDocumentStatus.FINALIZED,
                "INV-000042", "Customer Name", LocalDate.now(), LocalDate.now().plusDays(30),
                "Notes", new BigDecimal("100000.00"), new BigDecimal("100000.00"), "PKR",
                "NONE", "v1", 1L, userId, userId, userId, OffsetDateTime.now(), null, null, null,
                OffsetDateTime.now(), OffsetDateTime.now(), 1, Collections.emptyList()
        );
        when(commercialDocumentRepository.findWithLines(businessId, CommercialDocumentKind.INVOICE, docId))
                .thenReturn(Optional.of(record));
        when(obligationRepository.findByBusinessIdAndSourceTypeAndSourceId(businessId, ObligationSourceType.CUSTOMER_INVOICE, docId.toString()))
                .thenReturn(Optional.empty());

        Counterparty supplierCp = new Counterparty(businessId, "Steel Supplier", CounterpartyType.SUPPLIER, "email", "phone", "addr", "tax", "ref");
        when(counterpartyRepository.findByIdAndBusinessId(cpId, businessId))
                .thenReturn(Optional.of(supplierCp));

        assertThatThrownBy(() -> service.recognizeCommercialDocument(
                context, key, new CommercialRecognitionRequest(docId, CommercialDocumentKind.INVOICE, cpId, key, null), null
        ))
                .isInstanceOf(ObligationValidationException.class)
                .hasMessageContaining("Counterparty must be a customer");
    }
}

