package com.app.sme_health_backend.projectattribution;

import com.app.sme_health_backend.accounting.dto.TransactionDetailResponse;
import com.app.sme_health_backend.accounting.dto.TransactionPostRequest;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.AccountType;
import com.app.sme_health_backend.accounting.model.TransactionEventType;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.accounting.service.TransactionPostService;
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
import com.app.sme_health_backend.obligation.*;
import com.app.sme_health_backend.obligation.aging.ObligationDirection;
import com.app.sme_health_backend.projectattribution.dto.*;
import com.app.sme_health_backend.projectattribution.entity.ProjectCommercialDocumentLink;
import com.app.sme_health_backend.projectattribution.entity.ProjectFinancialObligationLink;
import com.app.sme_health_backend.projectattribution.model.AttributionCostCategory;
import com.app.sme_health_backend.projectattribution.model.AttributionSourceType;
import com.app.sme_health_backend.projectattribution.repository.ProjectCommercialDocumentLinkRepository;
import com.app.sme_health_backend.projectattribution.repository.ProjectFinancialAttributionRepository;
import com.app.sme_health_backend.projectattribution.repository.ProjectFinancialObligationLinkRepository;
import com.app.sme_health_backend.projectattribution.service.ProjectCommitmentService;
import com.app.sme_health_backend.projectattribution.service.ProjectFinancialAttributionService;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.whatsapp.provider=mock")
@AutoConfigureMockMvc
class ProjectCommitmentPostgreSqlIT extends DisposablePostgres {

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
    private FinancialObligationRepository obligationRepository;

    @Autowired
    private ObligationPaymentAllocationRepository paymentAllocationRepository;

    @Autowired
    private CommercialDocumentService commercialDocumentService;

    @Autowired
    private ProjectFinancialAttributionRepository attributionRepository;

    @Autowired
    private ProjectFinancialAttributionService attributionService;

    @Autowired
    private ProjectCommercialDocumentLinkRepository commercialDocumentLinkRepository;

    @Autowired
    private ProjectFinancialObligationLinkRepository obligationLinkRepository;

    @Autowired
    private ProjectCommitmentService commitmentService;

    @Autowired
    private TransactionPostService postService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    private UserBusinessFixture ownerFixture;

    @BeforeEach
    void setUp() throws Exception {
        ownerFixture = createFixture("comm-owner-" + UUID.randomUUID().toString().substring(0, 8), MembershipRole.OWNER);
    }

    @Test
    @DisplayName("Fixture A: Section 10 Mandatory Numerical Fixture (Export Order A-102)")
    void testSection10_MandatoryNumericalFixture() throws Exception {
        UUID businessId = ownerFixture.businessId();
        UUID projectId = insertProject(businessId, "EXP-A102", "Export Order A-102", "PKR");

        // 1. Approved planned cost: PKR 330,000
        insertApprovedBudget(businessId, projectId, 1, new BigDecimal("400000.0000"), new BigDecimal("330000.0000"), List.of(
                new BudgetLine("MATERIAL", "Export fabric", new BigDecimal("200000.0000")),
                new BudgetLine("LABOUR", "Stitching & cutting", new BigDecimal("80000.0000")),
                new BudgetLine("FREIGHT", "Container freight", new BigDecimal("30000.0000")),
                new BudgetLine("OVERHEAD", "Facility allocation", new BigDecimal("20000.0000"))
        ));

        FinancialAccount bankAcc = createAccount(businessId, "Operating Bank", AccountType.BANK, "PKR", 2, "1000000.00");

        // 2. Counterparty & Linked supplier payable: PKR 100,000
        Counterparty supplier = createCounterparty(businessId, "Al-Karam Textiles", CounterpartyType.SUPPLIER);
        FinancialObligation payable = createObligation(businessId, supplier.getId(), ObligationDirection.PAYABLE,
                new BigDecimal("100000.0000"), "PKR", "PO-9001");

        // 3. Amount paid against payable: PKR 40,000 (real transaction + allocation)
        UUID txPayableSettlement = postPayment(businessId, bankAcc.getId(), new BigDecimal("40000.00"), "Settlement for PO-9001");
        createAllocation(businessId, payable.getId(), txPayableSettlement, new BigDecimal("40000.0000"));

        // Additional payments to make total cash payments = PKR 195,000 (40k + 80k + 50k + 25k)
        UUID txFabricExtra = postPayment(businessId, bankAcc.getId(), new BigDecimal("80000.00"), "Direct fabric payment");
        UUID txLabour = postPayment(businessId, bankAcc.getId(), new BigDecimal("50000.00"), "Cutting and stitching wages");
        UUID txFreight = postPayment(businessId, bankAcc.getId(), new BigDecimal("25000.00"), "Freight agent fee");

        // Attribute the 4 cash transactions under C05A -> Total 195,000
        attributeSourceViaApi(ownerFixture, projectId, txPayableSettlement, AttributionSourceType.TRANSACTION, new BigDecimal("40000.00"), AttributionCostCategory.MATERIAL);
        attributeSourceViaApi(ownerFixture, projectId, txFabricExtra, AttributionSourceType.TRANSACTION, new BigDecimal("80000.00"), AttributionCostCategory.MATERIAL);
        attributeSourceViaApi(ownerFixture, projectId, txLabour, AttributionSourceType.TRANSACTION, new BigDecimal("50000.00"), AttributionCostCategory.LABOUR);
        attributeSourceViaApi(ownerFixture, projectId, txFreight, AttributionSourceType.TRANSACTION, new BigDecimal("25000.00"), AttributionCostCategory.FREIGHT);

        // 4. Link supplier payable to project via API
        FinancialObligationLinkRequest obLinkReq = new FinancialObligationLinkRequest(payable.getId(), new BigDecimal("100000.0000"), "PO-9001 for Order A-102");
        mvc.perform(post("/api/finance/projects/" + projectId + "/obligation-links")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(obLinkReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalAmount").value(100000.0))
                .andExpect(jsonPath("$.paidOrCollectedAmount").value(40000.0))
                .andExpect(jsonPath("$.outstandingAmount").value(60000.0))
                .andExpect(jsonPath("$.hasAttributedPayment").value(true))
                .andExpect(jsonPath("$.attributedCashAmount").value(40000.0));

        // 5. Additional non-posting commercial bill: PKR 50,000
        BusinessAccessContext ctx = new BusinessAccessContext(ownerFixture.user().getId(), businessId, MembershipRole.OWNER);
        var billDraft = commercialDocumentService.createDraft(ctx, CommercialDocumentKind.BILL, new CommercialDocumentDraftRequest(
                "Packages Ltd",
                LocalDate.now().toString(),
                LocalDate.now().plusDays(30).toString(),
                "Packaging cartons for A-102",
                List.of(new CommercialLineRequest("Cartons 5-ply", "500.00", "pcs", "100.00")),
                null
        ));
        var finalizedBill = commercialDocumentService.finalizeDraft(ctx, CommercialDocumentKind.BILL, billDraft.id(), new CommercialTransitionRequest(0L, null));
        assertThat(new BigDecimal(finalizedBill.total())).isEqualByComparingTo(new BigDecimal("50000.0000"));

        // Link commercial bill to project via API
        CommercialDocumentLinkRequest docLinkReq = new CommercialDocumentLinkRequest(finalizedBill.id(), new BigDecimal("50000.0000"), "Carton bill for A-102");
        mvc.perform(post("/api/finance/projects/" + projectId + "/commercial-document-links")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(docLinkReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentTotal").value(50000.0))
                .andExpect(jsonPath("$.allocatedAmount").value(50000.0))
                .andExpect(jsonPath("$.accountingStatus").value("COMMERCIAL_UNPOSTED"))
                .andExpect(jsonPath("$.isMatchedToFinancialObligation").value(false));

        // 6. Verify Section 10 Expected Reporting
        mvc.perform(get("/api/finance/projects/" + projectId + "/commitments")
                        .cookie(ownerFixture.session())
                        .header("X-XSRF-TOKEN", ownerFixture.csrfToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(projectId.toString()))
                .andExpect(jsonPath("$.plannedCost").value(330000.0))
                .andExpect(jsonPath("$.attributedCashOutflows").value(195000.0))
                .andExpect(jsonPath("$.outstandingSupplierPayables").value(60000.0))
                .andExpect(jsonPath("$.unpostedCommercialBills").value(50000.0))
                .andExpect(jsonPath("$.cashHeadroom").value(135000.0))
                .andExpect(jsonPath("$.totalOutstandingCommitments").value(110000.0))
                .andExpect(jsonPath("$.profitabilityStatus").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.linkedObligationsCount").value(1))
                .andExpect(jsonPath("$.linkedCommercialDocumentsCount").value(1))
                .andExpect(jsonPath("$.unmatchedCommercialDocumentsCount").value(1));

        // Verify identical response on /financial-overview alias
        mvc.perform(get("/api/finance/projects/" + projectId + "/financial-overview")
                        .cookie(ownerFixture.session())
                        .header("X-XSRF-TOKEN", ownerFixture.csrfToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plannedCost").value(330000.0))
                .andExpect(jsonPath("$.attributedCashOutflows").value(195000.0))
                .andExpect(jsonPath("$.cashHeadroom").value(135000.0))
                .andExpect(jsonPath("$.totalOutstandingCommitments").value(110000.0));
    }

    @Test
    @DisplayName("Fixture B: Split allocation across multiple projects & trigger limit enforcement")
    void testFixtureB_SplitAllocationAndTriggerEnforcement() throws Exception {
        UUID businessId = ownerFixture.businessId();
        UUID projectA = insertProject(businessId, "PROJ-A", "Project Alpha", "PKR");
        UUID projectB = insertProject(businessId, "PROJ-B", "Project Beta", "PKR");
        UUID projectC = insertProject(businessId, "PROJ-C", "Project Gamma", "PKR");

        Counterparty supplier = createCounterparty(businessId, "Steel Mills Ltd", CounterpartyType.SUPPLIER);
        FinancialObligation payable = createObligation(businessId, supplier.getId(), ObligationDirection.PAYABLE,
                new BigDecimal("100000.0000"), "PKR", "PO-STEEL-1");

        // Split 1: Link 60,000 to Project A
        FinancialObligationLinkRequest reqA = new FinancialObligationLinkRequest(payable.getId(), new BigDecimal("60000.0000"), "Project A share");
        mvc.perform(post("/api/finance/projects/" + projectA + "/obligation-links")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(reqA)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.allocatedAmount").value(60000.0));

        // Split 2: Link 40,000 to Project B
        FinancialObligationLinkRequest reqB = new FinancialObligationLinkRequest(payable.getId(), new BigDecimal("40000.0000"), "Project B share");
        mvc.perform(post("/api/finance/projects/" + projectB + "/obligation-links")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(reqB)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.allocatedAmount").value(40000.0));

        // Split 3: Attempting to link another 10,000 to Project C exceeds 100,000 ceiling -> 400 Bad Request
        FinancialObligationLinkRequest reqC = new FinancialObligationLinkRequest(payable.getId(), new BigDecimal("10000.0000"), "Project C over-allocation");
        mvc.perform(post("/api/finance/projects/" + projectC + "/obligation-links")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(reqC)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Fixture C: Commercial document non-posting boundary verification")
    void testFixtureC_CommercialDocumentNonPostingBoundary() throws Exception {
        UUID businessId = ownerFixture.businessId();
        UUID projectId = insertProject(businessId, "PROJ-BOUNDARY", "Boundary Test Project", "PKR");

        FinancialAccount bankAcc = createAccount(businessId, "Untouched Bank", AccountType.BANK, "PKR", 2, "500000.00");
        long initialTransactionsCount = jdbc.queryForObject("SELECT count(*) FROM financial_transactions WHERE business_id = ?", Long.class, businessId);
        long initialObligationsCount = obligationRepository.count();

        BusinessAccessContext ctx = new BusinessAccessContext(ownerFixture.user().getId(), businessId, MembershipRole.OWNER);
        var billDraft = commercialDocumentService.createDraft(ctx, CommercialDocumentKind.BILL, new CommercialDocumentDraftRequest(
                "Vendor Boundary",
                LocalDate.now().toString(),
                LocalDate.now().plusDays(15).toString(),
                "Testing non-posting boundary",
                List.of(new CommercialLineRequest("Item X", "1.00", "pcs", "75000.00")),
                null
        ));
        var finalizedBill = commercialDocumentService.finalizeDraft(ctx, CommercialDocumentKind.BILL, billDraft.id(), new CommercialTransitionRequest(0L, null));

        // Link to project
        CommercialDocumentLinkRequest docLinkReq = new CommercialDocumentLinkRequest(finalizedBill.id(), new BigDecimal("75000.0000"), "Link non-posting bill");
        mvc.perform(post("/api/finance/projects/" + projectId + "/commercial-document-links")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(docLinkReq)))
                .andExpect(status().isCreated());

        // Verify: Transactions count untouched (no ledger movement)
        long currentTransactionsCount = jdbc.queryForObject("SELECT count(*) FROM financial_transactions WHERE business_id = ?", Long.class, businessId);
        assertThat(currentTransactionsCount).isEqualTo(initialTransactionsCount);

        // Verify: No new rows in financial obligations
        assertThat(obligationRepository.count()).isEqualTo(initialObligationsCount);

        // Verify: No new rows in project financial attributions (actual cash not affected)
        assertThat(attributionRepository.findByBusinessIdAndProjectIdOrderByAttributionDateDescCreatedAtDesc(businessId, projectId)).isEmpty();
    }

    @Test
    @DisplayName("Fixture D: Append-only unlinking and audit preservation")
    void testFixtureD_AppendOnlyUnlinking() throws Exception {
        UUID businessId = ownerFixture.businessId();
        UUID projectId = insertProject(businessId, "PROJ-UNLINK", "Unlink Test Project", "PKR");

        Counterparty vendor = createCounterparty(businessId, "Vendor Unlink", CounterpartyType.SUPPLIER);
        FinancialObligation payable = createObligation(businessId, vendor.getId(), ObligationDirection.PAYABLE,
                new BigDecimal("50000.0000"), "PKR", "PO-UNLINK-1");

        // Link obligation
        FinancialObligationLinkRequest obLinkReq = new FinancialObligationLinkRequest(payable.getId(), new BigDecimal("50000.0000"), "Initial link");
        String obLinkContent = mvc.perform(post("/api/finance/projects/" + projectId + "/obligation-links")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(obLinkReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        ProjectObligationItemResponse obLinkResp = objectMapper.readValue(obLinkContent, ProjectObligationItemResponse.class);
        UUID linkId = obLinkResp.linkId();

        // Verify commitments includes the 50,000 payable
        mvc.perform(get("/api/finance/projects/" + projectId + "/commitments")
                        .cookie(ownerFixture.session())
                        .header("X-XSRF-TOKEN", ownerFixture.csrfToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outstandingSupplierPayables").value(50000.0));

        // Unlink obligation via API
        FinancialObligationUnlinkRequest unlinkReq = new FinancialObligationUnlinkRequest("Wrong project assigned");
        mvc.perform(post("/api/finance/projects/" + projectId + "/obligation-links/" + linkId + "/unlink")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(unlinkReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkStatus").value("UNLINKED"));

        // Commitments summary now reports 0 outstanding payables
        mvc.perform(get("/api/finance/projects/" + projectId + "/commitments")
                        .cookie(ownerFixture.session())
                        .header("X-XSRF-TOKEN", ownerFixture.csrfToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outstandingSupplierPayables").value(0.0));

        // Verify row is physically preserved in database with UNLINKED status
        ProjectFinancialObligationLink dbLink = obligationLinkRepository.findById(linkId).orElseThrow();
        assertThat(dbLink.getLinkStatus()).isEqualTo("UNLINKED");
        assertThat(dbLink.getUnlinkReason()).isEqualTo("Wrong project assigned");
        assertNotNull(dbLink.getUnlinkedAt());
        assertThat(dbLink.getUnlinkedBy()).isEqualTo(ownerFixture.user().getId());
    }

    @Test
    @DisplayName("Fixture E: Foreign currency obligation retention")
    void testFixtureE_ForeignCurrencyObligationRetention() throws Exception {
        UUID businessId = ownerFixture.businessId();
        UUID projectId = insertProject(businessId, "PROJ-FX-OB", "Multicurrency Project", "PKR");

        Counterparty globalVendor = createCounterparty(businessId, "Global Machinery Inc", CounterpartyType.SUPPLIER);
        FinancialObligation usdPayable = createObligation(businessId, globalVendor.getId(), ObligationDirection.PAYABLE,
                new BigDecimal("5000.0000"), "USD", "PO-USD-101");

        FinancialObligationLinkRequest req = new FinancialObligationLinkRequest(usdPayable.getId(), new BigDecimal("5000.0000"), "Imported lathe");
        mvc.perform(post("/api/finance/projects/" + projectId + "/obligation-links")
                        .cookie(ownerFixture.session())
                        .with(realCsrf(ownerFixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.originalAmount").value(5000.0))
                .andExpect(jsonPath("$.outstandingAmount").value(5000.0));
    }

    @Test
    @DisplayName("Fixture F: Cross-business isolation and RBAC authorization")
    void testFixtureF_CrossBusinessIsolationAndRbac() throws Exception {
        UUID businessA = ownerFixture.businessId();
        UUID projectA = insertProject(businessA, "PROJ-A-ISO", "Business A Project", "PKR");

        Counterparty vendorA = createCounterparty(businessA, "Vendor A", CounterpartyType.SUPPLIER);
        FinancialObligation payableA = createObligation(businessA, vendorA.getId(), ObligationDirection.PAYABLE,
                new BigDecimal("25000.0000"), "PKR", "PO-A");

        // Create Business B and user
        UserBusinessFixture bizBFixture = createFixture("biz-b-user-" + UUID.randomUUID().toString().substring(0, 8), MembershipRole.OWNER);

        // User B attempts to link Business A obligation to Project A -> 404 or 403
        FinancialObligationLinkRequest crossReq = new FinancialObligationLinkRequest(payableA.getId(), new BigDecimal("25000.0000"), "Cross biz attack");
        mvc.perform(post("/api/finance/projects/" + projectA + "/obligation-links")
                        .cookie(bizBFixture.session())
                        .with(realCsrf(bizBFixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(crossReq)))
                .andExpect(status().isNotFound());

        // Create VIEWER in Business A
        UserBusinessFixture viewerFixture = createFixture("viewer-" + UUID.randomUUID().toString().substring(0, 8), MembershipRole.VIEWER, businessA);

        // Viewer can read commitments
        mvc.perform(get("/api/finance/projects/" + projectA + "/commitments")
                        .cookie(viewerFixture.session())
                        .header("X-XSRF-TOKEN", viewerFixture.csrfToken()))
                .andExpect(status().isOk());

        // Viewer cannot mutate links -> 403 Forbidden
        mvc.perform(post("/api/finance/projects/" + projectA + "/obligation-links")
                        .cookie(viewerFixture.session())
                        .with(realCsrf(viewerFixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(crossReq)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Fixture G: Runtime role finsight_app cannot physically DELETE from attribution and linkage tables")
    void testFixtureG_RuntimeRoleCannotPhysicallyDelete() {
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataAccessException.class,
                () -> jdbc.execute("DELETE FROM project_financial_attributions")
        );
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataAccessException.class,
                () -> jdbc.execute("DELETE FROM project_financial_obligation_links")
        );
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataAccessException.class,
                () -> jdbc.execute("DELETE FROM project_commercial_document_links")
        );
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private Counterparty createCounterparty(UUID businessId, String name, CounterpartyType type) {
        Counterparty cp = new Counterparty(businessId, name, type, null, null, null, null, "Active counterparty");
        return counterparties.saveAndFlush(cp);
    }

    private FinancialObligation createObligation(
            UUID businessId,
            UUID counterpartyId,
            ObligationDirection direction,
            BigDecimal amount,
            String currency,
            String ref
    ) {
        FinancialObligation ob = new FinancialObligation(
                businessId,
                counterpartyId,
                direction,
                ObligationSourceType.SUPPLIER_BILL,
                ref,
                ref,
                LocalDate.now(),
                LocalDate.now().plusDays(30),
                amount,
                currency,
                "Test obligation " + ref,
                ownerFixture.user().getId()
        );
        return obligationRepository.saveAndFlush(ob);
    }

    private ObligationPaymentAllocation createAllocation(
            UUID businessId,
            UUID obligationId,
            UUID txId,
            BigDecimal amount
    ) {
        ObligationPaymentAllocation alloc = new ObligationPaymentAllocation(
                businessId,
                obligationId,
                txId,
                amount,
                LocalDate.now(),
                ownerFixture.user().getId()
        );
        return paymentAllocationRepository.saveAndFlush(alloc);
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

    private void insertApprovedBudget(UUID businessId, UUID projectId, int revision, BigDecimal plannedRevenue, BigDecimal plannedCost, List<BudgetLine> lines) {
        UUID versionId = UUID.randomUUID();
        BigDecimal plannedMargin = plannedRevenue.subtract(plannedCost);
        BigDecimal plannedMarginPct = new BigDecimal("17.50");

        jdbc.update("""
                INSERT INTO project_budget_versions (
                    id, business_id, project_id, revision, status, base_currency,
                    planned_revenue, planned_cost, planned_margin, planned_margin_pct,
                    calculation_version, created_by, updated_by
                ) VALUES (?, ?, ?, ?, 'DRAFT', 'PKR', ?, ?, ?, ?, 'f05-plan-v1', ?, ?)
                """, versionId, businessId, projectId, revision, plannedRevenue, plannedCost, plannedMargin, plannedMarginPct,
                ownerFixture.user().getId(), ownerFixture.user().getId());

        int lineNum = 1;
        for (BudgetLine l : lines) {
            jdbc.update("""
                    INSERT INTO project_budget_lines (
                        id, business_id, project_id, budget_version_id, line_number,
                        category, description, quantity, unit, unit_cost, planned_line_cost
                    ) VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, 1.000000, 'JOB', ?, ?)
                    """, businessId, projectId, versionId, lineNum++, l.category(), l.desc(), l.plannedCost(), l.plannedCost());
        }

        jdbc.update("""
                UPDATE project_budget_versions
                SET status = 'APPROVED',
                    approved_by = ?,
                    approved_at = now()
                WHERE id = ? AND business_id = ?
                """, ownerFixture.user().getId(), versionId, businessId);
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

    private void attributeSourceViaApi(UserBusinessFixture fixture, UUID projectId, UUID sourceId, AttributionSourceType type, BigDecimal amount, AttributionCostCategory category) throws Exception {
        AttributionCreateRequest req = new AttributionCreateRequest(
                type,
                sourceId,
                amount,
                category,
                "API attribution test",
                LocalDate.now(),
                null
        );
        mvc.perform(post("/api/finance/projects/" + projectId + "/attributions")
                        .cookie(fixture.session())
                        .with(realCsrf(fixture.session()))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    private FinancialAccount createAccount(
            UUID businessId,
            String name,
            AccountType type,
            String currency,
            int decimals,
            String openingBalance
    ) {
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

    private UserBusinessFixture createFixture(String emailPrefix, MembershipRole role) throws Exception {
        return createFixture(emailPrefix, role, null);
    }

    private UserBusinessFixture createFixture(String emailPrefix, MembershipRole role, UUID existingBizId) throws Exception {
        AppUser user = new AppUser();
        user.setEmail(emailPrefix + "@test.finsight.local");
        user.setFullName("C05 Test User");
        user.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setAccountStatus(com.app.sme_health_backend.identity.model.AccountStatus.ACTIVE);
        user.setMustChangePassword(false);
        user = users.saveAndFlush(user);

        UUID businessId = existingBizId != null ? existingBizId : UUID.randomUUID();
        if (existingBizId == null) {
            businesses.saveAndFlush(new Business(businessId));
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
    private record BudgetLine(String category, String desc, BigDecimal plannedCost) {}
}
