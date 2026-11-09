package com.app.sme_health_backend.projectattribution;

import com.app.sme_health_backend.accounting.dto.TransactionDetailResponse;
import com.app.sme_health_backend.accounting.dto.TransactionPostRequest;
import com.app.sme_health_backend.accounting.dto.TransactionReverseRequest;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.entity.FinancialTransaction;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.model.TransactionEventType;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.accounting.repository.FinancialTransactionRepository;
import com.app.sme_health_backend.accounting.service.TransactionPostService;
import com.app.sme_health_backend.accounting.service.TransactionReverseService;
import com.app.sme_health_backend.commercial.dto.CommercialDocumentDraftRequest;
import com.app.sme_health_backend.commercial.dto.CommercialLineRequest;
import com.app.sme_health_backend.commercial.dto.CommercialTransitionRequest;
import com.app.sme_health_backend.commercial.model.CommercialDocumentKind;
import com.app.sme_health_backend.commercial.service.CommercialDocumentService;
import com.app.sme_health_backend.counterparty.Counterparty;
import com.app.sme_health_backend.counterparty.CounterpartyRepository;
import com.app.sme_health_backend.counterparty.CounterpartyStatus;
import com.app.sme_health_backend.counterparty.CounterpartyType;
import com.app.sme_health_backend.documents.entity.UploadedDocument;
import com.app.sme_health_backend.documents.processing.DocumentStatus;
import com.app.sme_health_backend.documents.repository.UploadedDocumentRepository;
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
import com.app.sme_health_backend.projectattribution.dto.*;
import com.app.sme_health_backend.projectattribution.entity.ProjectFinancialAttribution;
import com.app.sme_health_backend.projectattribution.entity.ProjectOcrEvidenceLink;
import com.app.sme_health_backend.projectattribution.model.*;
import com.app.sme_health_backend.projectattribution.repository.ProjectFinancialAttributionRepository;
import com.app.sme_health_backend.projectattribution.repository.ProjectOcrEvidenceLinkRepository;
import com.app.sme_health_backend.projectattribution.service.ProjectCommitmentService;
import com.app.sme_health_backend.projectattribution.service.ProjectFinancialAttributionService;
import com.app.sme_health_backend.projectattribution.service.ProjectOcrEvidenceService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.whatsapp.provider=mock")
@AutoConfigureMockMvc
class ProjectOcrEvidencePostgreSqlIT extends DisposablePostgres {

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
    private FinancialAccountRepository accounts;

    @Autowired
    private CounterpartyRepository counterparties;

    @Autowired
    private FinancialTransactionRepository transactions;

    @Autowired
    private UploadedDocumentRepository documents;

    @Autowired
    private ProjectFinancialAttributionRepository attributionRepository;

    @Autowired
    private ProjectFinancialAttributionService attributionService;

    @Autowired
    private ProjectOcrEvidenceLinkRepository ocrEvidenceLinkRepository;

    @Autowired
    private ProjectOcrEvidenceService ocrEvidenceService;

    @Autowired
    private ProjectCommitmentService commitmentService;

    @Autowired
    private CommercialDocumentService commercialDocumentService;

    @Autowired
    private TransactionPostService postService;

    @Autowired
    private TransactionReverseService reverseService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    private UserBusinessFixture ownerFixture;

    @BeforeEach
    void setUp() throws Exception {
        ownerFixture = createFixture("ocr-owner-" + UUID.randomUUID().toString().substring(0, 8), MembershipRole.OWNER);
    }

    @Test
    @DisplayName("Fixture A: Reviewed receipt linked to posted payment — Authoritative cash integrity & zero ledger mutation")
    void testFixtureA_ReviewedReceiptLinkedToPostedPayment_AuthoritativeCashIntegrity() throws Exception {
        UUID businessId = ownerFixture.businessId();
        UUID projectId = insertProject(businessId, "PRJ-A", "Project Alpha", "PKR");
        FinancialAccount bankAcc = createAccount(businessId, "Operating Bank", AccountType.BANK, "PKR", 2, "1000000.00");

        // 1. Post actual payment of 100,000 PKR
        UUID txId = postPayment(businessId, bankAcc.getId(), new BigDecimal("100000.00"), "Al-Karam Fabric Purchase");
        long initialTxCount = transactions.count();

        // 2. Attribute payment to project
        AttributionResponse attr = attributeSourceViaApi(ownerFixture, projectId, txId, AttributionSourceType.TRANSACTION,
                new BigDecimal("100000.0000"), AttributionCostCategory.MATERIAL);

        // 3. Create reviewed/confirmed OCR document of 100,000 PKR
        UUID docId = createOcrDocument(businessId, "receipt-fabric-100k.pdf", new BigDecimal("100000.00"), "Al-Karam Fabric", "materials", DocumentStatus.confirmed);

        // 4. Link OCR evidence to project and attribution via REST
        OcrEvidenceLinkRequest linkReq = new OcrEvidenceLinkRequest(
                docId,
                attr.id(),
                null,
                null,
                ProjectOcrMatchMethod.MANUAL_ASSISTED,
                "Matched to fabric payment",
                null
        );

        mvc.perform(post("/api/finance/projects/{projectId}/ocr-evidence", projectId)
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(linkReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.evidenceStatus").value("ATTRIBUTED"))
                .andExpect(jsonPath("$.ocrAmount").value(100000.00))
                .andExpect(jsonPath("$.hasDiscrepancy").value(false))
                .andExpect(jsonPath("$.discrepancyAmount").value(0.0));

        // 5. Assert: Cash actuals remain strictly 100,000 PKR, NO new transactions or movements created
        long finalTxCount = transactions.count();
        assertThat(finalTxCount).isEqualTo(initialTxCount);

        List<ProjectOcrEvidenceItemResponse> evidenceList = ocrEvidenceService.listProjectOcrEvidence(businessId, projectId);
        assertThat(evidenceList).hasSize(1);
        assertThat(evidenceList.get(0).evidenceStatus()).isEqualTo(ProjectOcrEvidenceStatus.ATTRIBUTED);
        assertThat(evidenceList.get(0).matchedTransactionAmount()).isEqualByComparingTo("100000.0000");
    }

    @Test
    @DisplayName("Fixture B: Two-project split — 100k payment & receipt split 60k to Project A, 40k to Project B")
    void testFixtureB_TwoProjectSplit() throws Exception {
        UUID businessId = ownerFixture.businessId();
        UUID projectA = insertProject(businessId, "PRJ-SPLIT-A", "Split Project A", "PKR");
        UUID projectB = insertProject(businessId, "PRJ-SPLIT-B", "Split Project B", "PKR");
        FinancialAccount bankAcc = createAccount(businessId, "Bank", AccountType.BANK, "PKR", 2, "500000.00");

        UUID txId = postPayment(businessId, bankAcc.getId(), new BigDecimal("100000.00"), "Bulk timber shipment");

        AttributionResponse attrA = attributeSourceViaApi(ownerFixture, projectA, txId, AttributionSourceType.TRANSACTION,
                new BigDecimal("60000.0000"), AttributionCostCategory.MATERIAL);

        AttributionResponse attrB = attributeSourceViaApi(ownerFixture, projectB, txId, AttributionSourceType.TRANSACTION,
                new BigDecimal("40000.0000"), AttributionCostCategory.MATERIAL);

        UUID docId = createOcrDocument(businessId, "timber-bulk-100k.pdf", new BigDecimal("100000.00"), "Timber Mills", "materials", DocumentStatus.confirmed);

        // Link to Project A
        ocrEvidenceService.linkOcrEvidenceToProject(businessId, projectA, new OcrEvidenceLinkRequest(
                docId, attrA.id(), null, null, ProjectOcrMatchMethod.MANUAL_ASSISTED, "Project A 60k share", null
        ), ownerFixture.user().getId());

        // Link to Project B
        ocrEvidenceService.linkOcrEvidenceToProject(businessId, projectB, new OcrEvidenceLinkRequest(
                docId, attrB.id(), null, null, ProjectOcrMatchMethod.MANUAL_ASSISTED, "Project B 40k share", null
        ), ownerFixture.user().getId());

        // Check candidates endpoint for Project A
        mvc.perform(get("/api/finance/projects/{projectId}/ocr-candidates", projectA)
                        .cookie(ownerFixture.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.documentId == '" + docId + "')].alreadyLinkedProjectCount").value(2))
                .andExpect(jsonPath("$[?(@.documentId == '" + docId + "')].isAlreadyLinkedToThisProject").value(true));

        // Check duplicate flag in Project A
        List<ProjectOcrEvidenceItemResponse> evidenceA = ocrEvidenceService.listProjectOcrEvidence(businessId, projectA);
        assertThat(evidenceA).hasSize(1);
        assertThat(evidenceA.get(0).isDuplicate()).isTrue();
        assertThat(evidenceA.get(0).duplicateCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Fixture C: Reviewed receipt without posted source (25k) — Zero cash impact on project actuals")
    void testFixtureC_ReviewedReceiptWithoutPostedSource_ZeroCashImpact() throws Exception {
        UUID businessId = ownerFixture.businessId();
        UUID projectId = insertProject(businessId, "PRJ-UNPOSTED", "Unposted Project", "PKR");

        UUID docId = createOcrDocument(businessId, "stationery-25k.pdf", new BigDecimal("25000.00"), "Stationery Mart", "office", DocumentStatus.confirmed);

        OcrEvidenceLinkRequest linkReq = new OcrEvidenceLinkRequest(
                docId, null, null, null, ProjectOcrMatchMethod.UNMATCHED, "Pending bank settlement", null
        );

        mvc.perform(post("/api/finance/projects/{projectId}/ocr-evidence", projectId)
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(linkReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.evidenceStatus").value("REVIEWED_UNMATCHED"))
                .andExpect(jsonPath("$.ocrAmount").value(25000.00))
                .andExpect(jsonPath("$.matchMethod").value("UNMATCHED"));

        // Cash actuals remain 0
        List<ProjectFinancialAttribution> actuals = attributionRepository.findByBusinessIdAndProjectIdOrderByAttributionDateDescCreatedAtDesc(businessId, projectId);
        assertThat(actuals).isEmpty();
    }

    @Test
    @DisplayName("Fixture D: OCR discrepancy warning — 85k receipt vs 80k posted payment")
    void testFixtureD_OcrDiscrepancyWarning() throws Exception {
        UUID businessId = ownerFixture.businessId();
        UUID projectId = insertProject(businessId, "PRJ-DISC", "Discrepancy Project", "PKR");
        FinancialAccount bankAcc = createAccount(businessId, "Bank", AccountType.BANK, "PKR", 2, "200000.00");

        UUID txId = postPayment(businessId, bankAcc.getId(), new BigDecimal("80000.00"), "Hardware components");
        AttributionResponse attr = attributeSourceViaApi(ownerFixture, projectId, txId, AttributionSourceType.TRANSACTION,
                new BigDecimal("80000.0000"), AttributionCostCategory.MATERIAL);

        UUID docId = createOcrDocument(businessId, "hardware-85k.pdf", new BigDecimal("85000.00"), "Hardware Store", "materials", DocumentStatus.confirmed);

        OcrEvidenceLinkRequest linkReq = new OcrEvidenceLinkRequest(
                docId, attr.id(), null, null, ProjectOcrMatchMethod.MANUAL_ASSISTED, "Receipt shows 85k with tax", null
        );

        mvc.perform(post("/api/finance/projects/{projectId}/ocr-evidence", projectId)
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(linkReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hasDiscrepancy").value(true))
                .andExpect(jsonPath("$.discrepancyAmount").value(5000.00))
                .andExpect(jsonPath("$.ocrAmount").value(85000.00));

        // Confirm neither amount was mutated
        FinancialTransaction refreshedTx = transactions.findById(txId).orElseThrow();
        assertThat(refreshedTx.getAmount()).isEqualByComparingTo("80000.00");
    }

    @Test
    @DisplayName("Fixture E: Source reversal — 50k payment reversed marks linked OCR evidence REVERSED")
    void testFixtureE_SourceReversal_MarksEvidenceReversed() throws Exception {
        UUID businessId = ownerFixture.businessId();
        UUID projectId = insertProject(businessId, "PRJ-REV", "Reversal Project", "PKR");
        FinancialAccount bankAcc = createAccount(businessId, "Bank", AccountType.BANK, "PKR", 2, "200000.00");

        UUID txId = postPayment(businessId, bankAcc.getId(), new BigDecimal("50000.00"), "Erroneous charge");
        AttributionResponse attr = attributeSourceViaApi(ownerFixture, projectId, txId, AttributionSourceType.TRANSACTION,
                new BigDecimal("50000.0000"), AttributionCostCategory.MATERIAL);

        UUID docId = createOcrDocument(businessId, "charge-50k.pdf", new BigDecimal("50000.00"), "Vendor X", "materials", DocumentStatus.confirmed);

        ProjectOcrEvidenceItemResponse linked = ocrEvidenceService.linkOcrEvidenceToProject(businessId, projectId, new OcrEvidenceLinkRequest(
                docId, attr.id(), null, null, ProjectOcrMatchMethod.MANUAL_ASSISTED, "Linked", null
        ), ownerFixture.user().getId());

        assertThat(linked.evidenceStatus()).isEqualTo(ProjectOcrEvidenceStatus.ATTRIBUTED);

        // Reverse transaction
        BusinessAccessContext ctx = new BusinessAccessContext(ownerFixture.user().getId(), businessId, MembershipRole.OWNER);
        reverseService.postReversal(
                ctx,
                "rev-" + UUID.randomUUID(),
                txId,
                new TransactionReverseRequest("Payment cancelled by vendor")
        );

        // Assert OCR evidence is now marked REVERSED
        ProjectOcrEvidenceItemResponse refreshed = ocrEvidenceService.getOcrEvidenceDetail(businessId, projectId, linked.id());
        assertThat(refreshed.evidenceStatus()).isEqualTo(ProjectOcrEvidenceStatus.REVERSED);
        assertThat(refreshed.discrepancyNotes()).contains("reversed");
    }

    @Test
    @DisplayName("Fixture F: Commercial document boundary — Unposted bill vs OCR-linked cash payment")
    void testFixtureF_CommercialDocumentBoundary() throws Exception {
        UUID businessId = ownerFixture.businessId();
        UUID projectId = insertProject(businessId, "PRJ-COMM", "Commercial Boundary Project", "PKR");
        FinancialAccount bankAcc = createAccount(businessId, "Bank", AccountType.BANK, "PKR", 2, "500000.00");

        // 1. Create and finalize unposted supplier bill: 70,000 PKR
        BusinessAccessContext ctx = new BusinessAccessContext(ownerFixture.user().getId(), businessId, MembershipRole.OWNER);
        var billDraft = commercialDocumentService.createDraft(ctx, CommercialDocumentKind.BILL, new CommercialDocumentDraftRequest(
                "Atlas Steel",
                LocalDate.now().toString(),
                LocalDate.now().plusDays(30).toString(),
                "Steel shipment",
                List.of(new CommercialLineRequest("Steel bars", "700.00", "pcs", "100.00")),
                null
        ));
        var finalizedBill = commercialDocumentService.finalizeDraft(ctx, CommercialDocumentKind.BILL, billDraft.id(), new CommercialTransitionRequest(0L, null));

        // Link bill to project
        CommercialDocumentLinkRequest docLinkReq = new CommercialDocumentLinkRequest(finalizedBill.id(), new BigDecimal("70000.0000"), "PO link for steel");
        mvc.perform(post("/api/finance/projects/" + projectId + "/commercial-document-links")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(docLinkReq)))
                .andExpect(status().isCreated());

        // 2. Post actual cash payment: 30,000 PKR and link OCR receipt
        UUID txId = postPayment(businessId, bankAcc.getId(), new BigDecimal("30000.00"), "Advance payment for steel");
        AttributionResponse attr = attributeSourceViaApi(ownerFixture, projectId, txId, AttributionSourceType.TRANSACTION,
                new BigDecimal("30000.0000"), AttributionCostCategory.MATERIAL);

        UUID docId = createOcrDocument(businessId, "steel-advance-30k.pdf", new BigDecimal("30000.00"), "Atlas Steel", "materials", DocumentStatus.confirmed);
        ocrEvidenceService.linkOcrEvidenceToProject(businessId, projectId, new OcrEvidenceLinkRequest(
                docId, attr.id(), null, null, ProjectOcrMatchMethod.MANUAL_ASSISTED, "Receipt for advance", null
        ), ownerFixture.user().getId());

        // Verify summary
        ProjectCommitmentsSummaryResponse summary = commitmentService.getCommitmentsSummary(businessId, projectId);
        assertThat(summary.attributedCashOutflows()).isEqualByComparingTo("30000.0000");
        assertThat(summary.commercialUnpostedExposure()).isEqualByComparingTo("70000.0000");
        assertThat(summary.authoritativeFinancialOutstanding()).isEqualByComparingTo("0.0000");
    }

    @Test
    @DisplayName("Fixture G: Runtime role cannot physically DELETE from project_ocr_evidence_links")
    void testFixtureG_RuntimeRoleCannotPhysicallyDelete() {
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataAccessException.class,
                () -> jdbc.execute("DELETE FROM project_ocr_evidence_links")
        );
    }

    @Test
    @DisplayName("Fixture H: Four-entity multi-tenant isolation and foreign key tamper rejection")
    void testFixtureH_FourEntityMultiTenantIsolationAndTamperRejection() throws Exception {
        // 4 distinct entities: User A, Business A, User B, Business B
        UUID businessA = ownerFixture.businessId();
        UUID userA = ownerFixture.user().getId();

        UserBusinessFixture tenantBFixture = createFixture("tenant-b-" + UUID.randomUUID().toString().substring(0, 8), MembershipRole.OWNER);
        UUID businessB = tenantBFixture.businessId();
        UUID userB = tenantBFixture.user().getId();

        assertThat(businessA).isNotEqualTo(businessB);
        assertThat(userA).isNotEqualTo(userB);

        UUID projectA = insertProject(businessA, "PRJ-ISO-A", "Project A", "PKR");
        UUID projectB = insertProject(businessB, "PRJ-ISO-B", "Project B", "PKR");

        UUID docA = createOcrDocument(businessA, "receipt-bizA.pdf", new BigDecimal("45000.00"), "Vendor A", "supplies", DocumentStatus.confirmed);

        // 1. User A links Document A to Project A -> 201 Created
        OcrEvidenceLinkRequest validReq = new OcrEvidenceLinkRequest(
                docA, null, null, null, ProjectOcrMatchMethod.UNMATCHED, "Valid link biz A", null
        );
        mvc.perform(post("/api/finance/projects/{projectId}/ocr-evidence", projectA)
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentId").value(docA.toString()));

        // 2. User B attempts to link Document A to Project B -> 404 Not Found (cross-tenant doc access denied)
        OcrEvidenceLinkRequest crossDocReq = new OcrEvidenceLinkRequest(
                docA, null, null, null, ProjectOcrMatchMethod.UNMATCHED, "Cross-tenant theft attempt", null
        );
        mvc.perform(post("/api/finance/projects/{projectId}/ocr-evidence", projectB)
                        .cookie(tenantBFixture.session())
                        .with(realCsrf(tenantBFixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(crossDocReq)))
                .andExpect(status().isNotFound());

        // 3. User B attempts GET /api/documents/{docA} -> 404 Not Found
        mvc.perform(get("/api/documents/{id}", docA)
                        .cookie(tenantBFixture.session()))
                .andExpect(status().isNotFound());

        // 4. User A attempts cross-project link (Project B in Business A context) -> 404 Not Found
        mvc.perform(post("/api/finance/projects/{projectId}/ocr-evidence", projectB)
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validReq)))
                .andExpect(status().isNotFound());

        // 5. Direct SQL tamper: Attempt to insert into project_ocr_evidence_links with (businessB, projectB, docA)
        // Must fail with DataAccessException due to foreign key fk_proj_ocr_ev_document (business_id, document_id) -> uploaded_documents(user_id, id)
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO project_ocr_evidence_links (
                    id, business_id, project_id, document_id, evidence_status, match_method,
                    ocr_amount, version, created_by_user_id, created_at, updated_at
                ) VALUES (
                    ?, ?, ?, ?, 'REVIEWED_UNMATCHED', 'UNMATCHED',
                    1000.0000, 0, ?, now(), now()
                )
                """, UUID.randomUUID(), businessB, projectB, docA, userB))
                .isInstanceOf(org.springframework.dao.DataAccessException.class)
                .hasMessageContaining("fk_proj_ocr_ev_document");
    }

    @Test
    @DisplayName("Fixture I: Unreviewed extraction review boundary enforcement")
    void testFixtureI_UnreviewedExtractionReviewBoundaryEnforcement() throws Exception {
        UUID businessId = ownerFixture.businessId();
        UUID projectId = insertProject(businessId, "PRJ-REV-BND", "Review Boundary Project", "PKR");
        FinancialAccount bankAcc = createAccount(businessId, "Bank", AccountType.BANK, "PKR", 2, "300000.00");

        UUID txId = postPayment(businessId, bankAcc.getId(), new BigDecimal("45000.00"), "Unreviewed test payment");
        AttributionResponse attr = attributeSourceViaApi(ownerFixture, projectId, txId, AttributionSourceType.TRANSACTION,
                new BigDecimal("45000.0000"), AttributionCostCategory.MATERIAL);

        // Create an unreviewed document (status extracted, reviewedData null, confirmedData null)
        UUID unreviewedDocId = createRawUnreviewedOcrDocument(businessId, "unreviewed-invoice.pdf", new BigDecimal("45000.00"), "Raw Vendor");

        // 1. Direct HTTP link with attributionId on unreviewed document -> 400 Bad Request
        OcrEvidenceLinkRequest invalidAttrReq = new OcrEvidenceLinkRequest(
                unreviewedDocId, attr.id(), null, null, ProjectOcrMatchMethod.MANUAL_ASSISTED, "Illegal match", null
        );
        mvc.perform(post("/api/finance/projects/{projectId}/ocr-evidence", projectId)
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(invalidAttrReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Document must be human-reviewed and confirmed before matching or attributing to financial sources"));

        // 2. Direct HTTP link with financialSourceId on unreviewed document -> 400 Bad Request
        OcrEvidenceLinkRequest invalidTxReq = new OcrEvidenceLinkRequest(
                unreviewedDocId, null, txId, null, ProjectOcrMatchMethod.MANUAL_ASSISTED, "Illegal direct tx match", null
        );
        mvc.perform(post("/api/finance/projects/{projectId}/ocr-evidence", projectId)
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(invalidTxReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Document must be human-reviewed and confirmed before matching or attributing to financial sources"));

        // 3. Direct HTTP link without matches -> 201 Created as EXTRACTED_UNREVIEWED
        OcrEvidenceLinkRequest validUnmatchedReq = new OcrEvidenceLinkRequest(
                unreviewedDocId, null, null, null, ProjectOcrMatchMethod.UNMATCHED, "Unreviewed preliminary evidence", null
        );
        var createRes = mvc.perform(post("/api/finance/projects/{projectId}/ocr-evidence", projectId)
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validUnmatchedReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.evidenceStatus").value("EXTRACTED_UNREVIEWED"))
                .andReturn();

        ProjectOcrEvidenceItemResponse linkResp = objectMapper.readValue(createRes.getResponse().getContentAsString(), ProjectOcrEvidenceItemResponse.class);

        // 4. Confirm match attempt while still unreviewed -> 400 Bad Request
        OcrMatchConfirmationRequest confirmReq = new OcrMatchConfirmationRequest(
                attr.id(), null, "Attempt match while unreviewed"
        );
        mvc.perform(post("/api/finance/projects/{projectId}/ocr-evidence/{linkId}/confirm-match", projectId, linkResp.id())
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(confirmReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Document must be human-reviewed and confirmed before matching to financial sources"));

        // 5. Human reviewer confirms the document in OCR workspace
        UploadedDocument docEntity = documents.findById(unreviewedDocId).orElseThrow();
        docEntity.setProcessingStatus(DocumentStatus.confirmed);
        docEntity.setReviewedData(docEntity.getExtractedData());
        docEntity.setConfirmedData(docEntity.getExtractedData());
        documents.saveAndFlush(docEntity);

        // 6. Confirm match now succeeds -> 200 OK
        mvc.perform(post("/api/finance/projects/{projectId}/ocr-evidence/{linkId}/confirm-match", projectId, linkResp.id())
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(confirmReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidenceStatus").value("ATTRIBUTED"))
                .andExpect(jsonPath("$.attributionId").value(attr.id().toString()));
    }

    @Test
    @DisplayName("Fixture J: Role-based access control — SALESPERSON denied access to project OCR evidence")
    void testFixtureJ_RoleBasedAccessControl_SalespersonDenied() throws Exception {
        UUID businessId = ownerFixture.businessId();
        UUID projectId = insertProject(businessId, "PRJ-RBAC-SP", "Salesperson RBAC Project", "PKR");
        UUID docId = createOcrDocument(businessId, "sp-receipt.pdf", new BigDecimal("10000.00"), "Vendor RBAC", "supplies", DocumentStatus.confirmed);

        // Create Salesperson user in the business
        AppUser spUser = new AppUser();
        spUser.setEmail("sp-" + UUID.randomUUID().toString().substring(0, 8) + "@test.finsight.local");
        spUser.setFullName("Restricted Salesperson");
        spUser.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        spUser.setAccountStatus(com.app.sme_health_backend.identity.model.AccountStatus.ACTIVE);
        spUser.setMustChangePassword(false);
        spUser = users.saveAndFlush(spUser);

        memberships.saveAndFlush(new BusinessMembership(spUser.getId(), businessId, MembershipRole.SALESPERSON, MembershipStatus.ACTIVE));
        Cookie spSession = loginAndSelect(spUser, businessId);

        // 1. Link attempt -> 403 Forbidden
        OcrEvidenceLinkRequest linkReq = new OcrEvidenceLinkRequest(
                docId, null, null, null, ProjectOcrMatchMethod.UNMATCHED, "Unauthorized attempt", null
        );
        mvc.perform(post("/api/finance/projects/{projectId}/ocr-evidence", projectId)
                        .cookie(spSession)
                        .with(realCsrf(spSession))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(linkReq)))
                .andExpect(status().isForbidden());

        // 2. Candidate list attempt -> 403 Forbidden
        mvc.perform(get("/api/finance/projects/{projectId}/ocr-candidates", projectId)
                        .cookie(spSession))
                .andExpect(status().isForbidden());

        // Owner creates valid link
        ProjectOcrEvidenceItemResponse ownerLink = ocrEvidenceService.linkOcrEvidenceToProject(
                businessId, projectId, linkReq, ownerFixture.user().getId()
        );

        // 3. Confirm match attempt -> 403 Forbidden
        OcrMatchConfirmationRequest matchReq = new OcrMatchConfirmationRequest(null, null, "Denied");
        mvc.perform(post("/api/finance/projects/{projectId}/ocr-evidence/{linkId}/confirm-match", projectId, ownerLink.id())
                        .cookie(spSession)
                        .with(realCsrf(spSession))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(matchReq)))
                .andExpect(status().isForbidden());

        // 4. Invalidate attempt -> 403 Forbidden
        OcrEvidenceInvalidateRequest invReq = new OcrEvidenceInvalidateRequest("Unauthorized invalidation");
        mvc.perform(post("/api/finance/projects/{projectId}/ocr-evidence/{linkId}/invalidate", projectId, ownerLink.id())
                        .cookie(spSession)
                        .with(realCsrf(spSession))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(invReq)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private UUID createRawUnreviewedOcrDocument(
            UUID businessId,
            String filename,
            BigDecimal amount,
            String vendor
    ) throws Exception {
        UUID docId = UUID.randomUUID();
        String json = objectMapper.writeValueAsString(Map.of(
                "amount", amount,
                "vendor_or_party", vendor,
                "category", "materials",
                "date", LocalDate.now().toString(),
                "confidence", 0.90,
                "document_type_detected", "invoice"
        ));

        UploadedDocument doc = new UploadedDocument();
        doc.setId(docId);
        doc.setUserId(businessId);
        doc.setFileUrl("/api/documents/" + docId + "/file");
        doc.setOriginalFilename(filename);
        doc.setProcessingStatus(DocumentStatus.extracted);
        doc.setExtractedData(json);
        doc.setReviewedData(null);
        doc.setConfirmedData(null);
        documents.saveAndFlush(doc);
        return docId;
    }

    private UUID createOcrDocument(
            UUID businessId,
            String filename,
            BigDecimal amount,
            String vendor,
            String category,
            DocumentStatus status
    ) throws Exception {
        UUID docId = UUID.randomUUID();
        String json = objectMapper.writeValueAsString(Map.of(
                "amount", amount,
                "vendor_or_party", vendor,
                "category", category,
                "date", LocalDate.now().toString(),
                "confidence", 0.98,
                "document_type_detected", "receipt"
        ));

        UploadedDocument doc = new UploadedDocument();
        doc.setId(docId);
        doc.setUserId(businessId);
        doc.setFileUrl("/api/documents/" + docId + "/file");
        doc.setOriginalFilename(filename);
        doc.setProcessingStatus(status);
        doc.setExtractedData(json);
        doc.setReviewedData(json);
        doc.setConfirmedData(json);
        documents.saveAndFlush(doc);
        return docId;
    }

    private UUID insertProject(UUID businessId, String code, String name, String currency) {
        UUID projectId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO projects (
                    id, business_id, code, name, kind, description, status,
                    start_date, target_end_date, base_currency, version,
                    created_by, updated_by, created_at, updated_at
                ) VALUES (
                    ?, ?, ?, ?, 'PROJECT', 'C05 test project', 'ACTIVE',
                    '2026-01-01', '2026-12-31', ?, 0,
                    ?, ?, now(), now()
                )
                """, projectId, businessId, code, name, currency, ownerFixture.user().getId(), ownerFixture.user().getId());
        return projectId;
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
        return accounts.saveAndFlush(acc);
    }

    private Counterparty createCounterparty(UUID businessId, String name, CounterpartyType type) {
        Counterparty cp = new Counterparty(businessId, name, type, "test@cp.local", "+923001234567", "Address", "TAX-123", "CP-TEST");
        return counterparties.saveAndFlush(cp);
    }

    private UUID postPayment(UUID businessId, UUID accountId, BigDecimal amount, String desc) {
        BusinessAccessContext context = new BusinessAccessContext(ownerFixture.user().getId(), businessId, MembershipRole.OWNER);
        TransactionPostRequest req = new TransactionPostRequest(
                accountId,
                TransactionEventType.OPERATING_EXPENSE_PAYMENT.name(),
                amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
                LocalDate.now().toString(),
                desc,
                null
        );
        TransactionDetailResponse res = postService.postTransaction(context, "post-" + UUID.randomUUID(), req);
        return res.id();
    }

    private AttributionResponse attributeSourceViaApi(
            UserBusinessFixture fixture,
            UUID projectId,
            UUID sourceId,
            AttributionSourceType type,
            BigDecimal amount,
            AttributionCostCategory category
    ) throws Exception {
        AttributionCreateRequest req = new AttributionCreateRequest(
                type,
                sourceId,
                amount,
                category,
                "API attribution test",
                LocalDate.now(),
                null
        );
        var res = mvc.perform(post("/api/finance/projects/" + projectId + "/attributions")
                        .cookie(fixture.session())
                        .with(realCsrf(fixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(res.getResponse().getContentAsString(), AttributionResponse.class);
    }

    private UserBusinessFixture createFixture(String emailPrefix, MembershipRole role) throws Exception {
        AppUser user = new AppUser();
        user.setEmail(emailPrefix + "@test.finsight.local");
        user.setFullName("C05 Test User");
        user.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setAccountStatus(com.app.sme_health_backend.identity.model.AccountStatus.ACTIVE);
        user.setMustChangePassword(false);
        user = users.saveAndFlush(user);

        UUID businessId = UUID.randomUUID();
        businesses.saveAndFlush(new Business(businessId));
        jdbc.update("INSERT INTO business_profiles(user_id, business_type) VALUES (?, 'retail') ON CONFLICT (user_id) DO NOTHING", businessId);
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
