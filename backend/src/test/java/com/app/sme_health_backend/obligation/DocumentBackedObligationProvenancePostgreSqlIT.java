package com.app.sme_health_backend.obligation;

import com.app.sme_health_backend.commercial.dto.CommercialDocumentDraftRequest;
import com.app.sme_health_backend.commercial.dto.CommercialLineRequest;
import com.app.sme_health_backend.commercial.dto.CommercialTransitionRequest;
import com.app.sme_health_backend.commercial.lifecycle.CommercialLifecycleService;
import com.app.sme_health_backend.commercial.model.CommercialDocumentKind;
import com.app.sme_health_backend.commercial.service.CommercialDocumentService;
import com.app.sme_health_backend.counterparty.Counterparty;
import com.app.sme_health_backend.counterparty.CounterpartyRepository;
import com.app.sme_health_backend.counterparty.CounterpartyType;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.entity.Business;
import com.app.sme_health_backend.identity.entity.BusinessMembership;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.model.MembershipStatus;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.identity.repository.BusinessMembershipRepository;
import com.app.sme_health_backend.identity.repository.BusinessRepository;
import com.app.sme_health_backend.obligation.dto.CommercialRecognitionRequest;
import com.app.sme_health_backend.obligation.dto.ObligationCreateRequest;
import com.app.sme_health_backend.obligation.dto.ObligationDetailResponse;
import com.app.sme_health_backend.obligation.dto.ObligationVoidRequest;
import com.app.sme_health_backend.testsupport.DisposablePostgres;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression for the document-backed obligation bypass found in the production-closure audit.
 *
 * <p>Before the fix the generic obligation API (a) accepted a client-supplied CUSTOMER_INVOICE/SUPPLIER_BILL source
 * pointing at a FinSight document's id, with any direction and amount — the P&amp;L then counted it as that invoice's
 * revenue and real recognition failed with "already recognized" — and (b) voided a recognized invoice/bill
 * obligation directly, leaving the document FINALIZED with its stock issue and notes still effective.
 */
@SpringBootTest(properties = "app.whatsapp.provider=mock")
class DocumentBackedObligationProvenancePostgreSqlIT extends DisposablePostgres {

    @Autowired private AppUserRepository users;
    @Autowired private BusinessRepository businesses;
    @Autowired private BusinessMembershipRepository memberships;
    @Autowired private CounterpartyRepository counterparties;
    @Autowired private CommercialDocumentService commercial;
    @Autowired private CommercialRecognitionService recognition;
    @Autowired private CommercialLifecycleService lifecycle;
    @Autowired private FinancialObligationService obligations;
    @Autowired private FinancialObligationRepository obligationRepository;
    @Autowired private JdbcTemplate jdbc;

    private BusinessAccessContext ctx;
    private Counterparty customer;

    @BeforeEach
    void setUp() {
        AppUser user = new AppUser();
        user.setEmail("provenance-" + UUID.randomUUID().toString().substring(0, 8) + "@test.finsight.local");
        user.setFullName("Provenance Owner");
        user.setPasswordHash("{noop}unused");
        user.setAccountStatus(com.app.sme_health_backend.identity.model.AccountStatus.ACTIVE);
        user.setMustChangePassword(false);
        user = users.saveAndFlush(user);
        UUID businessId = UUID.randomUUID();
        businesses.saveAndFlush(new Business(businessId));
        jdbc.update("INSERT INTO business_profiles(user_id, business_type) VALUES (?, 'retail') ON CONFLICT (user_id) DO NOTHING", businessId);
        memberships.saveAndFlush(new BusinessMembership(user.getId(), businessId, MembershipRole.OWNER, MembershipStatus.ACTIVE));
        ctx = new BusinessAccessContext(user.getId(), businessId, MembershipRole.OWNER);
        customer = counterparties.saveAndFlush(new Counterparty(businessId, "Ahmed Textiles", CounterpartyType.CUSTOMER,
                "a@cp.local", "+923001234567", "Address", "TAX-1", "CP-1"));
    }

    private UUID finalizedInvoice() {
        var draft = commercial.createDraft(ctx, CommercialDocumentKind.INVOICE, new CommercialDocumentDraftRequest(
                "Ahmed Textiles", LocalDate.now().minusDays(2).toString(), LocalDate.now().plusDays(28).toString(),
                "Fabric order", List.of(new CommercialLineRequest("Cotton fabric", "100.00", "meter", "500.00")), null));
        return commercial.finalizeDraft(ctx, CommercialDocumentKind.INVOICE, draft.id(), new CommercialTransitionRequest(0L, null)).id();
    }

    private UUID recognize(UUID documentId) {
        return recognition.recognizeCommercialDocument(ctx, "prov-" + UUID.randomUUID(),
                new CommercialRecognitionRequest(documentId, CommercialDocumentKind.INVOICE, customer.getId(), null, null),
                new MockHttpServletRequest()).obligationId();
    }

    private ObligationCreateRequest declare(String direction, String sourceType, String sourceId) {
        return new ObligationCreateRequest(customer.getId(), direction, "999.00", LocalDate.now().toString(), null,
                "REF-1", "declared", sourceType, sourceId);
    }

    @Test
    void genericApiCannotForgeFinSightDocumentProvenance() {
        UUID documentId = finalizedInvoice();

        assertThatThrownBy(() -> obligations.createObligation(ctx, declare("RECEIVABLE", "CUSTOMER_INVOICE", documentId.toString())))
                .isInstanceOf(ObligationValidationException.class)
                .satisfies(e -> assertThat(((ObligationValidationException) e).getErrors()).containsKey("sourceId"));
        assertThat(obligationRepository.findByBusinessIdAndSourceTypeAndSourceId(
                ctx.businessId(), ObligationSourceType.CUSTOMER_INVOICE, documentId.toString())).isEmpty();

        // The real document can still be recognized afterwards (previously blocked with "already recognized").
        UUID obligationId = recognize(documentId);
        assertThat(obligationRepository.findById(obligationId).orElseThrow().getOriginalAmount()).isEqualByComparingTo("50000");
    }

    @Test
    void declaredInvoiceAndBillDirectionMustMatchTheirSourceType() {
        assertThatThrownBy(() -> obligations.createObligation(ctx, declare("PAYABLE", "CUSTOMER_INVOICE", "EXT-INV-1")))
                .isInstanceOf(ObligationValidationException.class)
                .satisfies(e -> assertThat(((ObligationValidationException) e).getErrors()).containsKey("sourceType"));
    }

    @Test
    void externallyIssuedInvoicesRemainDeclarableAndVoidable() {
        ObligationDetailResponse declared = obligations.createObligation(ctx, declare("RECEIVABLE", "CUSTOMER_INVOICE", "EXT-INV-7"));
        ObligationDetailResponse voided = obligations.voidObligation(ctx, declared.id(), new ObligationVoidRequest("Entered twice"));
        assertThat(voided.status()).isEqualTo("VOIDED");
    }

    @Test
    void recognizedInvoiceObligationIsVoidedOnlyThroughTheDocumentLifecycle() {
        UUID documentId = finalizedInvoice();
        UUID obligationId = recognize(documentId);

        assertThatThrownBy(() -> obligations.voidObligation(ctx, obligationId, new ObligationVoidRequest("bypass")))
                .isInstanceOf(ObligationConflictException.class)
                .hasMessageContaining("void the document itself");
        assertThat(obligationRepository.findById(obligationId).orElseThrow().getStatus()).isEqualTo(ObligationStatus.OPEN);
        assertThat(jdbc.queryForObject("SELECT status FROM commercial_documents WHERE id = ?", String.class, documentId))
                .isEqualTo("FINALIZED");

        lifecycle.voidRecognized(ctx, documentId, "Customer cancelled the order");

        assertThat(obligationRepository.findById(obligationId).orElseThrow().getStatus()).isEqualTo(ObligationStatus.VOIDED);
        assertThat(jdbc.queryForObject("SELECT status FROM commercial_documents WHERE id = ?", String.class, documentId))
                .isEqualTo("VOID");
    }
}
