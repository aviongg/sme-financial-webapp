package com.app.sme_health_backend.obligation;

import com.app.sme_health_backend.accounting.entity.AccountMovement;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.entity.FinancialTransaction;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.repository.AccountMovementRepository;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.accounting.repository.FinancialTransactionRepository;
import com.app.sme_health_backend.commercial.dto.CommercialDocumentDraftRequest;
import com.app.sme_health_backend.commercial.dto.CommercialLineRequest;
import com.app.sme_health_backend.commercial.dto.CommercialTransitionRequest;
import com.app.sme_health_backend.commercial.model.CommercialDocumentKind;
import com.app.sme_health_backend.commercial.service.CommercialDocumentService;
import com.app.sme_health_backend.counterparty.Counterparty;
import com.app.sme_health_backend.counterparty.CounterpartyRepository;
import com.app.sme_health_backend.counterparty.CounterpartyStatus;
import com.app.sme_health_backend.counterparty.CounterpartyType;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.dto.CsrfResponse;
import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.entity.Business;
import com.app.sme_health_backend.identity.entity.BusinessMembership;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.model.MembershipStatus;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.identity.repository.BusinessMembershipRepository;
import com.app.sme_health_backend.identity.repository.BusinessRepository;
import com.app.sme_health_backend.obligation.aging.ObligationDirection;
import com.app.sme_health_backend.obligation.dto.*;
import com.app.sme_health_backend.testsupport.DisposablePostgres;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.whatsapp.provider=mock")
@AutoConfigureMockMvc
class CommercialRecognitionPostgreSqlIT extends DisposablePostgres {

    private static final String DEFAULT_PASSWORD = "Password123!";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private BusinessRepository businesses;

    @Autowired
    private BusinessMembershipRepository memberships;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CounterpartyRepository counterparties;

    @Autowired
    private FinancialObligationRepository obligationRepository;

    @Autowired
    private ObligationPaymentAllocationRepository paymentAllocationRepository;

    @Autowired
    private FinancialAccountRepository accountRepository;

    @Autowired
    private FinancialTransactionRepository transactionRepository;

    @Autowired
    private AccountMovementRepository movementRepository;

    @Autowired
    private CommercialDocumentService commercialService;

    @Autowired
    private PaymentAllocationService paymentAllocationService;

    @Autowired
    private CommercialRecognitionService recognitionService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    private UserBusinessFixture ownerFixture;

    @BeforeEach
    void setUp() throws Exception {
        ownerFixture = createFixture("recog-owner-" + UUID.randomUUID().toString().substring(0, 8), MembershipRole.OWNER);
    }

    @Test
    @DisplayName("Fixture A: Finalized customer invoice (INV-000042, PKR 115,000) creates RECEIVABLE with zero bank mutation")
    void testFixtureA_FinalizedCustomerInvoice_CreatesReceivable() throws Exception {
        UUID businessId = ownerFixture.businessId();
        BusinessAccessContext ctx = new BusinessAccessContext(ownerFixture.user().getId(), businessId, MembershipRole.OWNER);

        // 1. Create and finalize commercial invoice of 115,000 PKR with natural number INV-000042
        jdbc.update("INSERT INTO commercial_number_sequences(business_id, kind, next_value) VALUES (?, 'INVOICE', 42) ON CONFLICT (business_id, kind) DO UPDATE SET next_value = 42", businessId);
        var draft = commercialService.createDraft(ctx, CommercialDocumentKind.INVOICE, new CommercialDocumentDraftRequest(
                "Ahmed Textiles",
                "2026-11-10",
                "2026-12-10",
                "Textile sale order",
                List.of(new CommercialLineRequest("Cotton fabric", "1150.00", "meter", "100.00")),
                null
        ));
        var finalized = commercialService.finalizeDraft(ctx, CommercialDocumentKind.INVOICE, draft.id(), new CommercialTransitionRequest(0L, null));

        // 2. Create customer counterparty
        Counterparty customer = createCounterparty(businessId, "Ahmed Textiles", CounterpartyType.CUSTOMER);

        long initialTxCount = transactionRepository.count();
        long initialMovementCount = movementRepository.count();

        // 3. Recognize commercial invoice via REST
        CommercialRecognitionRequest req = new CommercialRecognitionRequest(
                finalized.id(),
                CommercialDocumentKind.INVOICE,
                customer.getId(),
                "recog-inv-42-" + UUID.randomUUID(),
                "Recognized Invoice INV-000042"
        );

        var result = mvc.perform(post("/api/finance/obligations/recognize-commercial-document")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.direction").value("RECEIVABLE"))
                .andExpect(jsonPath("$.sourceType").value("CUSTOMER_INVOICE"))
                .andExpect(jsonPath("$.sourceId").value(finalized.id().toString()))
                .andExpect(jsonPath("$.documentNumber").value("INV-000042"))
                .andExpect(jsonPath("$.counterpartyId").value(customer.getId().toString()))
                .andExpect(jsonPath("$.originalAmount").value(115000.00))
                .andExpect(jsonPath("$.outstandingAmount").value(115000.00))
                .andExpect(jsonPath("$.allocatedAmount").value(0.00))
                .andExpect(jsonPath("$.currency").value("PKR"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.recognitionProvenance").value("F04_COMMERCIAL_RECOGNITION_V1"))
                .andReturn();

        CommercialRecognitionResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), CommercialRecognitionResponse.class);
        assertNotNull(response.obligationId());

        // 4. Assert zero cash/bank mutation
        assertThat(transactionRepository.count()).isEqualTo(initialTxCount);
        assertThat(movementRepository.count()).isEqualTo(initialMovementCount);

        // 5. Assert commercial document remains strictly intact with financial_effect = 'NONE'
        String effect = jdbc.queryForObject("SELECT financial_effect FROM commercial_documents WHERE id = ?", String.class, finalized.id());
        assertThat(effect).isEqualTo("NONE");

        // 6. Assert obligation in repository
        FinancialObligation obligation = obligationRepository.findById(response.obligationId()).orElseThrow();
        assertThat(obligation.getDirection()).isEqualTo(ObligationDirection.RECEIVABLE);
        assertThat(obligation.getSourceType()).isEqualTo(ObligationSourceType.CUSTOMER_INVOICE);
        assertThat(obligation.getOriginalAmount()).isEqualByComparingTo("115000.0000");
    }

    @Test
    @DisplayName("Fixture B: Finalized supplier bill (BILL-000031, PKR 80,000) creates PAYABLE with zero cash paid")
    void testFixtureB_FinalizedSupplierBill_CreatesPayable() throws Exception {
        UUID businessId = ownerFixture.businessId();
        BusinessAccessContext ctx = new BusinessAccessContext(ownerFixture.user().getId(), businessId, MembershipRole.OWNER);

        // 1. Create and finalize commercial bill of 80,000 PKR with natural number BILL-000031
        jdbc.update("INSERT INTO commercial_number_sequences(business_id, kind, next_value) VALUES (?, 'BILL', 31) ON CONFLICT (business_id, kind) DO UPDATE SET next_value = 31", businessId);
        var draft = commercialService.createDraft(ctx, CommercialDocumentKind.BILL, new CommercialDocumentDraftRequest(
                "Atlas Steel Supplies",
                "2026-11-10",
                "2026-12-10",
                "Steel beams procurement",
                List.of(new CommercialLineRequest("Steel bars", "800.00", "pcs", "100.00")),
                null
        ));
        var finalized = commercialService.finalizeDraft(ctx, CommercialDocumentKind.BILL, draft.id(), new CommercialTransitionRequest(0L, null));

        // 2. Create supplier counterparty
        Counterparty supplier = createCounterparty(businessId, "Atlas Steel Supplies", CounterpartyType.SUPPLIER);

        long initialTxCount = transactionRepository.count();
        long initialMovementCount = movementRepository.count();

        // 3. Recognize commercial bill via REST
        CommercialRecognitionRequest req = new CommercialRecognitionRequest(
                finalized.id(),
                CommercialDocumentKind.BILL,
                supplier.getId(),
                "recog-bill-31-" + UUID.randomUUID(),
                "Recognized Bill BILL-000031"
        );

        mvc.perform(post("/api/finance/obligations/recognize-commercial-document")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.direction").value("PAYABLE"))
                .andExpect(jsonPath("$.sourceType").value("SUPPLIER_BILL"))
                .andExpect(jsonPath("$.sourceId").value(finalized.id().toString()))
                .andExpect(jsonPath("$.documentNumber").value("BILL-000031"))
                .andExpect(jsonPath("$.counterpartyId").value(supplier.getId().toString()))
                .andExpect(jsonPath("$.originalAmount").value(80000.00))
                .andExpect(jsonPath("$.outstandingAmount").value(80000.00))
                .andExpect(jsonPath("$.allocatedAmount").value(0.00))
                .andExpect(jsonPath("$.status").value("OPEN"));

        // 4. Zero bank movements
        assertThat(transactionRepository.count()).isEqualTo(initialTxCount);
        assertThat(movementRepository.count()).isEqualTo(initialMovementCount);
    }

    @Test
    @DisplayName("Fixture C: Draft commercial document recognition is rejected (HTTP 400)")
    void testFixtureC_DraftCommercialDocument_Rejected() throws Exception {
        UUID businessId = ownerFixture.businessId();
        BusinessAccessContext ctx = new BusinessAccessContext(ownerFixture.user().getId(), businessId, MembershipRole.OWNER);

        var draft = commercialService.createDraft(ctx, CommercialDocumentKind.INVOICE, new CommercialDocumentDraftRequest(
                "Draft Customer",
                "2026-11-10",
                "2026-12-10",
                "Unfinalized order",
                List.of(new CommercialLineRequest("Item", "10.00", "pcs", "500.00")),
                null
        ));

        Counterparty customer = createCounterparty(businessId, "Draft Customer", CounterpartyType.CUSTOMER);

        CommercialRecognitionRequest req = new CommercialRecognitionRequest(
                draft.id(),
                CommercialDocumentKind.INVOICE,
                customer.getId(),
                "draft-attempt-" + UUID.randomUUID(),
                "Attempt draft recognition"
        );

        mvc.perform(post("/api/finance/obligations/recognize-commercial-document")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.status").value("Cannot recognize draft commercial document. Document must be FINALIZED before financial recognition."));

        // Confirm zero obligations created
        assertThat(obligationRepository.findByBusinessIdAndSourceId(businessId, draft.id().toString())).isEmpty();
    }

    @Test
    @DisplayName("Fixture D: Void commercial document recognition is rejected (HTTP 400)")
    void testFixtureD_VoidCommercialDocument_Rejected() throws Exception {
        UUID businessId = ownerFixture.businessId();
        BusinessAccessContext ctx = new BusinessAccessContext(ownerFixture.user().getId(), businessId, MembershipRole.OWNER);

        var draft = commercialService.createDraft(ctx, CommercialDocumentKind.BILL, new CommercialDocumentDraftRequest(
                "Void Supplier",
                "2026-11-10",
                "2026-12-10",
                "Cancelled order",
                List.of(new CommercialLineRequest("Item", "1.00", "pcs", "5000.00")),
                null
        ));
        var finalized = commercialService.finalizeDraft(ctx, CommercialDocumentKind.BILL, draft.id(), new CommercialTransitionRequest(0L, null));
        commercialService.voidFinalized(ctx, CommercialDocumentKind.BILL, finalized.id(), new CommercialTransitionRequest(1L, "Vendor out of stock"));

        Counterparty supplier = createCounterparty(businessId, "Void Supplier", CounterpartyType.SUPPLIER);

        CommercialRecognitionRequest req = new CommercialRecognitionRequest(
                finalized.id(),
                CommercialDocumentKind.BILL,
                supplier.getId(),
                "void-attempt-" + UUID.randomUUID(),
                "Attempt void recognition"
        );

        mvc.perform(post("/api/finance/obligations/recognize-commercial-document")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.status").value("Cannot recognize voided commercial document."));

        assertThat(obligationRepository.findByBusinessIdAndSourceId(businessId, finalized.id().toString())).isEmpty();
    }

    @Test
    @DisplayName("Fixture E: Duplicate prevention — Idempotent replay, changed-payload conflict, and duplicate rejection")
    void testFixtureE_DuplicatePreventionAndIdempotency() throws Exception {
        UUID businessId = ownerFixture.businessId();
        BusinessAccessContext ctx = new BusinessAccessContext(ownerFixture.user().getId(), businessId, MembershipRole.OWNER);

        var draft = commercialService.createDraft(ctx, CommercialDocumentKind.INVOICE, new CommercialDocumentDraftRequest(
                "Idem Client",
                "2026-11-10",
                "2026-12-10",
                "Idempotency test order",
                List.of(new CommercialLineRequest("Licence", "1.00", "ea", "50000.00")),
                null
        ));
        var finalized = commercialService.finalizeDraft(ctx, CommercialDocumentKind.INVOICE, draft.id(), new CommercialTransitionRequest(0L, null));
        Counterparty customer1 = createCounterparty(businessId, "Idem Client", CounterpartyType.CUSTOMER);
        Counterparty customer2 = createCounterparty(businessId, "Alt Client", CounterpartyType.CUSTOMER);

        String idemKey = "idem-key-" + UUID.randomUUID();
        CommercialRecognitionRequest req1 = new CommercialRecognitionRequest(
                finalized.id(),
                CommercialDocumentKind.INVOICE,
                customer1.getId(),
                idemKey,
                "First recognition call"
        );

        // 1. First call -> 201 Created
        var res1 = mvc.perform(post("/api/finance/obligations/recognize-commercial-document")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated())
                .andReturn();
        CommercialRecognitionResponse resp1 = objectMapper.readValue(res1.getResponse().getContentAsString(), CommercialRecognitionResponse.class);

        // 2. Second call with SAME idempotency key & SAME payload -> Idempotent Replay with same obligation ID
        var res2 = mvc.perform(post("/api/finance/obligations/recognize-commercial-document")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated())
                .andReturn();
        CommercialRecognitionResponse resp2 = objectMapper.readValue(res2.getResponse().getContentAsString(), CommercialRecognitionResponse.class);
        assertThat(resp2.obligationId()).isEqualTo(resp1.obligationId());

        // 3. Third call with SAME idempotency key but CHANGED payload (different counterparty) -> 409 Conflict
        CommercialRecognitionRequest reqChanged = new CommercialRecognitionRequest(
                finalized.id(),
                CommercialDocumentKind.INVOICE,
                customer2.getId(),
                idemKey,
                "Changed payload call"
        );
        mvc.perform(post("/api/finance/obligations/recognize-commercial-document")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(reqChanged)))
                .andExpect(status().isConflict());

        // 4. Fourth call with DIFFERENT idempotency key for ALREADY recognized document -> 409 Conflict
        CommercialRecognitionRequest reqNewKey = new CommercialRecognitionRequest(
                finalized.id(),
                CommercialDocumentKind.INVOICE,
                customer1.getId(),
                "new-key-" + UUID.randomUUID(),
                "New key for existing doc"
        );
        mvc.perform(post("/api/finance/obligations/recognize-commercial-document")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(reqNewKey)))
                .andExpect(status().isConflict());

        // Database has strictly 1 obligation for this document
        List<FinancialObligation> obs = obligationRepository.findByBusinessIdAndSourceId(businessId, finalized.id().toString());
        assertThat(obs).hasSize(1);
    }

    @Test
    @DisplayName("Fixture F: Counterparty type mismatch is rejected (HTTP 400)")
    void testFixtureF_CounterpartyTypeMismatch_Rejected() throws Exception {
        UUID businessId = ownerFixture.businessId();
        BusinessAccessContext ctx = new BusinessAccessContext(ownerFixture.user().getId(), businessId, MembershipRole.OWNER);

        // 1. Invoice with SUPPLIER counterparty
        var invoiceDraft = commercialService.createDraft(ctx, CommercialDocumentKind.INVOICE, new CommercialDocumentDraftRequest(
                "Client", "2026-11-10", "2026-12-10", "Note",
                List.of(new CommercialLineRequest("Item", "1.00", "ea", "1000.00")), null
        ));
        var finalizedInv = commercialService.finalizeDraft(ctx, CommercialDocumentKind.INVOICE, invoiceDraft.id(), new CommercialTransitionRequest(0L, null));
        Counterparty supplierOnly = createCounterparty(businessId, "Steel Supplier", CounterpartyType.SUPPLIER);

        CommercialRecognitionRequest invReq = new CommercialRecognitionRequest(
                finalizedInv.id(), CommercialDocumentKind.INVOICE, supplierOnly.getId(), "inv-mismatch", null
        );
        mvc.perform(post("/api/finance/obligations/recognize-commercial-document")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(invReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.counterpartyId").value("Counterparty must be a customer (CUSTOMER or BOTH) for customer invoice recognition."));

        // 2. Bill with CUSTOMER counterparty
        var billDraft = commercialService.createDraft(ctx, CommercialDocumentKind.BILL, new CommercialDocumentDraftRequest(
                "Vendor", "2026-11-10", "2026-12-10", "Note",
                List.of(new CommercialLineRequest("Item", "1.00", "ea", "2000.00")), null
        ));
        var finalizedBill = commercialService.finalizeDraft(ctx, CommercialDocumentKind.BILL, billDraft.id(), new CommercialTransitionRequest(0L, null));
        Counterparty customerOnly = createCounterparty(businessId, "Retail Customer", CounterpartyType.CUSTOMER);

        CommercialRecognitionRequest billReq = new CommercialRecognitionRequest(
                finalizedBill.id(), CommercialDocumentKind.BILL, customerOnly.getId(), "bill-mismatch", null
        );
        mvc.perform(post("/api/finance/obligations/recognize-commercial-document")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(billReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.counterpartyId").value("Counterparty must be a supplier (SUPPLIER or BOTH) for supplier bill recognition."));
    }

    @Test
    @DisplayName("Fixture G: Cross-business isolation — Business B cannot recognize or view Business A commercial document")
    void testFixtureG_CrossBusinessIsolation() throws Exception {
        UUID businessA = ownerFixture.businessId();
        BusinessAccessContext ctxA = new BusinessAccessContext(ownerFixture.user().getId(), businessA, MembershipRole.OWNER);

        var draftA = commercialService.createDraft(ctxA, CommercialDocumentKind.INVOICE, new CommercialDocumentDraftRequest(
                "Biz A Customer", "2026-11-10", "2026-12-10", "Secret doc",
                List.of(new CommercialLineRequest("Product", "1.00", "ea", "25000.00")), null
        ));
        var finalizedA = commercialService.finalizeDraft(ctxA, CommercialDocumentKind.INVOICE, draftA.id(), new CommercialTransitionRequest(0L, null));

        // Create tenant B
        UserBusinessFixture tenantBFixture = createFixture("tenant-b-" + UUID.randomUUID().toString().substring(0, 8), MembershipRole.OWNER);
        Counterparty customerB = createCounterparty(tenantBFixture.businessId(), "Biz B Customer", CounterpartyType.CUSTOMER);

        // User B attempts to recognize Business A's document
        CommercialRecognitionRequest crossReq = new CommercialRecognitionRequest(
                finalizedA.id(), CommercialDocumentKind.INVOICE, customerB.getId(), "cross-biz-key", "Theft attempt"
        );

        mvc.perform(post("/api/finance/obligations/recognize-commercial-document")
                        .cookie(tenantBFixture.session())
                        .with(realCsrf(tenantBFixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(crossReq)))
                .andExpect(status().isNotFound());

        // Status query from Business B also returns 404
        mvc.perform(get("/api/finance/obligations/commercial-recognition-status/{documentId}", finalizedA.id())
                        .cookie(tenantBFixture.session()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Fixture H: Concurrent overlapping requests on same finalized document creates exactly one obligation")
    void testFixtureH_ConcurrentOverlappingRequests_SingleObligation() throws Exception {
        UUID businessId = ownerFixture.businessId();
        BusinessAccessContext ctx = new BusinessAccessContext(ownerFixture.user().getId(), businessId, MembershipRole.OWNER);

        var draft = commercialService.createDraft(ctx, CommercialDocumentKind.INVOICE, new CommercialDocumentDraftRequest(
                "Concurrent Client", "2026-11-10", "2026-12-10", "Race test",
                List.of(new CommercialLineRequest("Item", "1.00", "ea", "90000.00")), null
        ));
        var finalized = commercialService.finalizeDraft(ctx, CommercialDocumentKind.INVOICE, draft.id(), new CommercialTransitionRequest(0L, null));
        Counterparty customer = createCounterparty(businessId, "Concurrent Client", CounterpartyType.CUSTOMER);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Callable<Integer> task1 = () -> {
            startLatch.await();
            try {
                CommercialRecognitionRequest req = new CommercialRecognitionRequest(
                        finalized.id(), CommercialDocumentKind.INVOICE, customer.getId(), "race-key-1", null
                );
                return mvc.perform(post("/api/finance/obligations/recognize-commercial-document")
                                .cookie(ownerFixture.session())
                                .with(realCsrf(ownerFixture.session()))
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(req)))
                        .andReturn().getResponse().getStatus();
            } catch (Exception e) {
                return 500;
            }
        };

        Callable<Integer> task2 = () -> {
            startLatch.await();
            try {
                CommercialRecognitionRequest req = new CommercialRecognitionRequest(
                        finalized.id(), CommercialDocumentKind.INVOICE, customer.getId(), "race-key-2", null
                );
                return mvc.perform(post("/api/finance/obligations/recognize-commercial-document")
                                .cookie(ownerFixture.session())
                                .with(realCsrf(ownerFixture.session()))
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(req)))
                        .andReturn().getResponse().getStatus();
            } catch (Exception e) {
                return 500;
            }
        };

        Future<Integer> f1 = executor.submit(task1);
        Future<Integer> f2 = executor.submit(task2);

        startLatch.countDown();
        int status1 = f1.get(10, TimeUnit.SECONDS);
        int status2 = f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly one should be 201 Created, the other 409 Conflict
        List<Integer> statuses = List.of(status1, status2);
        assertThat(statuses).containsExactlyInAnyOrder(201, 409);

        // Database has strictly 1 obligation
        List<FinancialObligation> obs = obligationRepository.findByBusinessIdAndSourceId(businessId, finalized.id().toString());
        assertThat(obs).hasSize(1);
    }

    @Test
    @DisplayName("Fixture I: Existing financial settlement compatibility — Recognized receivable settled via PaymentAllocationService")
    void testFixtureI_FinancialSettlementCompatibility() throws Exception {
        UUID businessId = ownerFixture.businessId();
        BusinessAccessContext ctx = new BusinessAccessContext(ownerFixture.user().getId(), businessId, MembershipRole.OWNER);

        // 1. Create and recognize invoice for 115,000 PKR
        var draft = commercialService.createDraft(ctx, CommercialDocumentKind.INVOICE, new CommercialDocumentDraftRequest(
                "Settlement Client", "2026-11-10", "2026-12-10", "To settle",
                List.of(new CommercialLineRequest("Goods", "1.00", "ea", "115000.00")), null
        ));
        var finalized = commercialService.finalizeDraft(ctx, CommercialDocumentKind.INVOICE, draft.id(), new CommercialTransitionRequest(0L, null));
        Counterparty customer = createCounterparty(businessId, "Settlement Client", CounterpartyType.CUSTOMER);

        CommercialRecognitionResponse recog = recognitionService.recognizeCommercialDocument(
                ctx, "settle-doc-key", new CommercialRecognitionRequest(finalized.id(), CommercialDocumentKind.INVOICE, customer.getId(), "settle-doc-key", null), null
        );

        // Assert 0 initial cash movements
        assertThat(paymentAllocationRepository.findByBusinessIdAndObligationIdOrderByEffectiveDateAscCreatedAtAsc(businessId, recog.obligationId())).isEmpty();

        // 2. Provision bank account with 500,000 PKR
        FinancialAccount bankAcc = createAccount(businessId, "Collection Bank", AccountType.BANK, "PKR", 2, "500000.00");

        // 3. Post payment allocation of 50,000 PKR against recognized obligation
        PaymentAllocateRequest allocReq = new PaymentAllocateRequest(
                customer.getId(),
                "RECEIVABLE",
                bankAcc.getId(),
                LocalDate.now().toString(),
                "50000.00",
                List.of(new AllocationItemRequest(recog.obligationId(), "50000.00")),
                "REF-100",
                "Partial invoice collection",
                false
        );

        PaymentAllocateResponse allocRes = paymentAllocationService.allocatePayment(ctx, "alloc-key-" + UUID.randomUUID(), allocReq);
        assertThat(allocRes.allocations()).hasSize(1);
        assertThat(allocRes.allocations().get(0).allocatedAmount()).isEqualTo("50000.00");
        assertThat(allocRes.allocations().get(0).remainingObligationOutstanding()).isEqualTo("65000.00");

        // 4. Verify GET /commercial-recognition-status/{documentId} returns updated settlement state
        mvc.perform(get("/api/finance/obligations/commercial-recognition-status/{documentId}", finalized.id())
                        .cookie(ownerFixture.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isRecognized").value(true))
                .andExpect(jsonPath("$.obligationId").value(recog.obligationId().toString()))
                .andExpect(jsonPath("$.originalAmount").value(115000.00))
                .andExpect(jsonPath("$.allocatedAmount").value(50000.00))
                .andExpect(jsonPath("$.outstandingAmount").value(65000.00))
                .andExpect(jsonPath("$.allocations[0].amount").value(50000.00));
    }

    @Test
    @DisplayName("Fixture J: Read-only recognition status — un-recognized vs recognized query with zero mutations")
    void testFixtureJ_ReadOnlyRecognitionStatus() throws Exception {
        UUID businessId = ownerFixture.businessId();
        BusinessAccessContext ctx = new BusinessAccessContext(ownerFixture.user().getId(), businessId, MembershipRole.OWNER);

        var draft = commercialService.createDraft(ctx, CommercialDocumentKind.BILL, new CommercialDocumentDraftRequest(
                "Vendor J", "2026-11-10", "2026-12-10", "Bill J",
                List.of(new CommercialLineRequest("Item", "1.00", "ea", "35000.00")), null
        ));
        var finalized = commercialService.finalizeDraft(ctx, CommercialDocumentKind.BILL, draft.id(), new CommercialTransitionRequest(0L, null));

        long initialObligations = obligationRepository.count();

        // 1. Unrecognized document status query -> isRecognized: false
        mvc.perform(get("/api/finance/obligations/commercial-recognition-status/{documentId}", finalized.id())
                        .cookie(ownerFixture.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isRecognized").value(false))
                .andExpect(jsonPath("$.documentKind").value("BILL"))
                .andExpect(jsonPath("$.originalAmount").value(35000.00));

        assertThat(obligationRepository.count()).isEqualTo(initialObligations);

        // 2. Recognize document
        Counterparty supplier = createCounterparty(businessId, "Vendor J", CounterpartyType.SUPPLIER);
        recognitionService.recognizeCommercialDocument(
                ctx, "key-j", new CommercialRecognitionRequest(finalized.id(), CommercialDocumentKind.BILL, supplier.getId(), "key-j", null), null
        );

        // 3. Recognized document status query -> isRecognized: true
        mvc.perform(get("/api/finance/obligations/commercial-recognition-status/{documentId}", finalized.id())
                        .cookie(ownerFixture.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isRecognized").value(true))
                .andExpect(jsonPath("$.documentKind").value("BILL"))
                .andExpect(jsonPath("$.originalAmount").value(35000.00))
                .andExpect(jsonPath("$.allocatedAmount").value(0.00))
                .andExpect(jsonPath("$.outstandingAmount").value(35000.00));
    }

    @Test
    @DisplayName("Fixture K: Role-based access control — SALESPERSON denied access to commercial recognition (HTTP 403)")
    void testFixtureK_RoleBasedAccessControl_SalespersonDenied() throws Exception {
        UUID businessId = ownerFixture.businessId();
        BusinessAccessContext ctx = new BusinessAccessContext(ownerFixture.user().getId(), businessId, MembershipRole.OWNER);

        var draft = commercialService.createDraft(ctx, CommercialDocumentKind.INVOICE, new CommercialDocumentDraftRequest(
                "Client K", "2026-11-10", "2026-12-10", "Invoice K",
                List.of(new CommercialLineRequest("Item", "1.00", "ea", "20000.00")), null
        ));
        var finalized = commercialService.finalizeDraft(ctx, CommercialDocumentKind.INVOICE, draft.id(), new CommercialTransitionRequest(0L, null));
        Counterparty customer = createCounterparty(businessId, "Client K", CounterpartyType.CUSTOMER);

        // Create Salesperson user in the business
        UserBusinessFixture spFixture = createFixture("sp-user-" + UUID.randomUUID().toString().substring(0, 8), MembershipRole.SALESPERSON, businessId);

        CommercialRecognitionRequest req = new CommercialRecognitionRequest(
                finalized.id(), CommercialDocumentKind.INVOICE, customer.getId(), "sp-key", null
        );

        // 1. POST recognition -> 403 Forbidden
        mvc.perform(post("/api/finance/obligations/recognize-commercial-document")
                        .cookie(spFixture.session())
                        .with(realCsrf(spFixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        // 2. GET status -> 403 Forbidden
        mvc.perform(get("/api/finance/obligations/commercial-recognition-status/{documentId}", finalized.id())
                        .cookie(spFixture.session()))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Counterparty createCounterparty(UUID businessId, String name, CounterpartyType type) {
        Counterparty cp = new Counterparty(businessId, name, type, "test@cp.local", "+923001234567", "Address", "TAX-123", "CP-TEST");
        return counterparties.saveAndFlush(cp);
    }

    private FinancialAccount createAccount(UUID businessId, String name, AccountType type, String currency, int decimals, String openingBalance) {
        FinancialAccount acc = new FinancialAccount(
                businessId,
                name,
                type,
                currency,
                decimals,
                new BigDecimal(openingBalance),
                LocalDate.of(2026, 1, 1),
                AccountStatus.ACTIVE,
                "Test Bank",
                "ACC-" + UUID.randomUUID(),
                "Testing notes"
        );
        return accountRepository.saveAndFlush(acc);
    }

    private UserBusinessFixture createFixture(String emailPrefix, MembershipRole role) throws Exception {
        return createFixture(emailPrefix, role, null);
    }

    private UserBusinessFixture createFixture(String emailPrefix, MembershipRole role, UUID existingBizId) throws Exception {
        AppUser user = new AppUser();
        user.setEmail(emailPrefix + "@test.finsight.local");
        user.setFullName("C04 Test User");
        user.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setAccountStatus(com.app.sme_health_backend.identity.model.AccountStatus.ACTIVE);
        user.setMustChangePassword(false);
        user = users.saveAndFlush(user);

        UUID businessId = existingBizId != null ? existingBizId : UUID.randomUUID();
        if (existingBizId == null) {
            businesses.saveAndFlush(new Business(businessId));
            jdbc.update("INSERT INTO business_profiles(user_id, business_type) VALUES (?, 'retail') ON CONFLICT (user_id) DO NOTHING", businessId);
        }
        memberships.saveAndFlush(new BusinessMembership(user.getId(), businessId, role, MembershipStatus.ACTIVE));

        Cookie session = loginAndSelect(user, businessId);
        String xsrfToken = getXsrfToken(session);
        return new UserBusinessFixture(user, businessId, session, xsrfToken);
    }

    private Cookie loginAndSelect(AppUser user, UUID businessId) throws Exception {
        var csrfResponse = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
        Cookie initialSession = csrfResponse.getCookie("FINSIGHT_SESSION");
        Cookie xsrf = csrfResponse.getCookie("XSRF-TOKEN");
        assertNotNull(xsrf);

        var login = post("/api/auth/login")
                .contentType("application/json")
                .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + DEFAULT_PASSWORD + "\"}")
                .cookie(xsrf)
                .header("X-XSRF-TOKEN", xsrf.getValue());
        if (initialSession != null) {
            login.cookie(initialSession);
        }

        Cookie session = mvc.perform(login).andExpect(status().isOk()).andReturn().getResponse().getCookie("FINSIGHT_SESSION");
        assertNotNull(session, "Login must establish a session cookie");

        mvc.perform(post("/api/businesses/active")
                        .cookie(session)
                        .with(realCsrf(session))
                        .contentType("application/json")
                        .content("{\"businessId\":\"" + businessId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessId").value(businessId.toString()));

        return session;
    }

    private RequestPostProcessor realCsrf(Cookie session) throws Exception {
        var response = mvc.perform(get("/api/auth/csrf").cookie(session)).andExpect(status().isOk()).andReturn().getResponse();
        Cookie xsrf = response.getCookie("XSRF-TOKEN");
        assertNotNull(xsrf);
        CsrfResponse token = objectMapper.readValue(response.getContentAsString(), CsrfResponse.class);
        return req -> {
            var cookies = new ArrayList<Cookie>(Arrays.asList(req.getCookies() == null ? new Cookie[0] : req.getCookies()));
            cookies.removeIf(c -> c.getName().equals(xsrf.getName()));
            cookies.add(xsrf);
            req.setCookies(cookies.toArray(Cookie[]::new));
            req.addHeader(token.headerName(), token.token());
            return req;
        };
    }

    private String getXsrfToken(Cookie session) throws Exception {
        var res = mvc.perform(get("/api/auth/csrf").cookie(session)).andExpect(status().isOk()).andReturn().getResponse();
        Cookie xsrf = res.getCookie("XSRF-TOKEN");
        return xsrf != null ? xsrf.getValue() : "";
    }

    private record UserBusinessFixture(AppUser user, UUID businessId, Cookie session, String csrfToken) {}
}

