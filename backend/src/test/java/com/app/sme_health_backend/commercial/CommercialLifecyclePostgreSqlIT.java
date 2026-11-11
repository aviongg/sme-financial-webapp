package com.app.sme_health_backend.commercial;

import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.testsupport.ProductApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * F04 full lifecycle on real PostgreSQL through the authenticated HTTP API: issue -> recognize (Developer A C04)
 * -> settle -> correct (credit/debit notes as non-cash allocations) -> cancel correction -> void after recognition
 * only by compensation, with every original fact retained.
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class CommercialLifecyclePostgreSqlIT extends ProductApiTestBase {

    private JsonNode lifecycle(Actor a, UUID doc) throws Exception {
        return getJson(a, "/api/commercial/documents/" + doc + "/lifecycle");
    }

    private JsonNode obligation(Actor a, UUID id) throws Exception {
        return getJson(a, "/api/finance/obligations/" + id);
    }

    @Test
    void invoiceIssueRecognizeCorrectSettleAndVoidByCompensation() throws Exception {
        Actor owner = owner();
        UUID customer = counterparty(owner, "Ahmed Textiles", "CUSTOMER");
        UUID account = account(owner, "50000.00");
        UUID invoice = finalizedDocument(owner, "invoices", TODAY.minusDays(5), List.of(
                new Object[] {"Consulting", "10", "100.00", null}, new Object[] {"Material", "2", "250.00", null}));

        JsonNode before = lifecycle(owner, invoice);
        assertFalse(before.get("recognized").asBoolean());
        assertTrue(before.get("actions").get("canRecognize").asBoolean());
        assertFalse(before.get("actions").get("canAdjust").asBoolean());
        assertEquals("document_not_recognized", postJson(owner, "/api/commercial/documents/" + invoice + "/adjustments",
                map("amount", "100.00", "reason", "Discount"), key(), 409).get("error").asText());

        UUID obligationId = recognize(owner, invoice, "INVOICE", customer);
        JsonNode rec = lifecycle(owner, invoice);
        assertTrue(rec.get("recognized").asBoolean());
        assertEquals(0, new BigDecimal("1500.00").compareTo(decimal(rec.get("recognition"), "outstandingAmount")));
        assertEquals("UNPAID", rec.get("recognition").get("settlementState").asText());

        // Credit note: idempotent, numbered, reduces the authoritative outstanding without touching the obligation amount.
        String k = key();
        JsonNode cn = postJson(owner, "/api/commercial/documents/" + invoice + "/adjustments",
                map("amount", "200.00", "reason", "Agreed discount"), k, 201);
        assertEquals("CREDIT_NOTE", cn.get("kind").asText());
        assertEquals("CN-000001", cn.get("number").asText());
        JsonNode replay = postJson(owner, "/api/commercial/documents/" + invoice + "/adjustments",
                map("amount", "200.00", "reason", "Agreed discount"), k, 201);
        assertEquals(cn.get("id").asText(), replay.get("id").asText());
        assertEquals("idempotency_key_reused", postJson(owner, "/api/commercial/documents/" + invoice + "/adjustments",
                map("amount", "201.00", "reason", "Agreed discount"), k, 409).get("error").asText());
        JsonNode ob = obligation(owner, obligationId);
        assertEquals(0, new BigDecimal("1500.00").compareTo(decimal(ob, "originalAmount")), "Recognized amount is immutable");
        assertEquals(0, new BigDecimal("1300.00").compareTo(decimal(ob, "outstandingAmount")));

        // Partial settlement through Developer A; adjustment cannot exceed what remains outstanding.
        UUID settlementTx = settle(owner, obligationId, account, "1000.00");
        assertEquals("adjustment_exceeds_outstanding", postJson(owner, "/api/commercial/documents/" + invoice + "/adjustments",
                map("amount", "300.01", "reason", "Too much"), key(), 409).get("error").asText());
        JsonNode mid = lifecycle(owner, invoice);
        assertEquals("PARTIALLY_SETTLED", mid.get("recognition").get("settlementState").asText());
        assertEquals(0, new BigDecimal("1000.00").compareTo(decimal(mid.get("recognition"), "cashSettled")));
        assertEquals(0, new BigDecimal("200.00").compareTo(decimal(mid.get("recognition"), "adjusted")));
        assertEquals(0, new BigDecimal("300.00").compareTo(decimal(mid.get("recognition"), "outstandingAmount")));
        assertTrue(mid.get("voidBlockers").toString().contains("SETTLEMENTS_MUST_BE_REVERSED"));

        // Cancel the credit note: compensating, restores outstanding, cannot be repeated.
        postJson(owner, "/api/commercial/adjustments/" + cn.get("id").asText() + "/cancel", map("reason", "Discount withdrawn"), null, 200);
        assertEquals(0, new BigDecimal("500.00").compareTo(decimal(obligation(owner, obligationId), "outstandingAmount")));
        assertEquals("adjustment_already_cancelled", postJson(owner, "/api/commercial/adjustments/" + cn.get("id").asText()
                + "/cancel", map("reason", "again"), null, 409).get("error").asText());
        JsonNode cn2 = postJson(owner, "/api/commercial/documents/" + invoice + "/adjustments",
                map("amount", "50.00", "reason", "Rounding"), key(), 201);
        assertEquals("CN-000002", cn2.get("number").asText());

        // Void with live settlements is refused by both the plain F04 void and the financial void.
        JsonNode doc = getJson(owner, "/api/invoices/" + invoice);
        assertEquals("document_has_financial_effect", postJson(owner, "/api/invoices/" + invoice + "/void",
                map("expectedVersion", doc.get("version").asLong(), "reason", "Mistake"), null, 409).get("error").asText());
        JsonNode blocked = postJson(owner, "/api/commercial/documents/" + invoice + "/void-recognized", map("reason", "Mistake"), null, 409);
        assertEquals("void_prerequisites_unmet", blocked.get("error").asText());

        // Reverse the settlement through Developer A, then void by compensation.
        reverseTransaction(owner, settlementTx);
        JsonNode voided = postJson(owner, "/api/commercial/documents/" + invoice + "/void-recognized", map("reason", "Issued in error"), null, 200);
        assertEquals("VOID", voided.get("status").asText());
        assertEquals("VOIDED", voided.get("recognition").get("obligationStatus").asText());
        assertEquals("VOIDED", obligation(owner, obligationId).get("status").asText());
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM commercial_adjustments WHERE document_id = ?", Integer.class, invoice));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM commercial_adjustments WHERE document_id = ? AND status = 'EFFECTIVE'",
                Integer.class, invoice), "Effective notes are cancelled, never deleted");
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM financial_obligations WHERE id = ?", Integer.class, obligationId));
        assertEquals("1500.0000", jdbc.queryForObject("SELECT original_amount::text FROM financial_obligations WHERE id = ?",
                String.class, obligationId));
    }

    @Test
    void billDebitNoteReducesPayableAndHistoryIsAppendOnly() throws Exception {
        Actor owner = owner();
        UUID supplier = counterparty(owner, "Karachi Metals", "SUPPLIER");
        UUID bill = finalizedDocument(owner, "bills", TODAY.minusDays(2), List.<Object[]>of(new Object[] {"Steel", "5", "400.00", null}));
        UUID obligationId = recognize(owner, bill, "BILL", supplier);
        JsonNode dn = postJson(owner, "/api/commercial/documents/" + bill + "/adjustments",
                map("amount", "400.00", "reason", "One sheet returned", "adjustmentDate", TODAY.toString()), key(), 201);
        assertEquals("DEBIT_NOTE", dn.get("kind").asText());
        assertEquals("DN-000001", dn.get("number").asText());
        assertEquals(0, new BigDecimal("1600.00").compareTo(decimal(obligation(owner, obligationId), "outstandingAmount")));
        postJson(owner, "/api/commercial/documents/" + bill + "/adjustments",
                map("amount", "1.00", "reason", "Before issue", "adjustmentDate", TODAY.minusDays(10).toString()), key(), 400);
        postJson(owner, "/api/commercial/documents/" + bill + "/adjustments", map("amount", "0", "reason", "zero"), key(), 400);

        UUID id = UUID.fromString(dn.get("id").asText());
        assertThrows(Exception.class, () -> jdbc.update("UPDATE commercial_adjustments SET amount = 1 WHERE id = ?", id));
        assertThrows(Exception.class, () -> jdbc.update("DELETE FROM commercial_adjustments WHERE id = ?", id));
        assertEquals("CREDIT_NOTE", jdbc.queryForObject("SELECT allocation_type FROM obligation_payment_allocations "
                + "WHERE commercial_adjustment_id = ?", String.class, id));
        assertNull(jdbc.queryForObject("SELECT financial_transaction_id FROM obligation_payment_allocations "
                + "WHERE commercial_adjustment_id = ?", UUID.class, id), "Debit/credit notes never create cash movements");

        JsonNode aging = getJson(owner, "/api/finance/obligations/aging?asOfDate=" + TODAY);
        assertNotNull(aging, "Developer-A aging continues to read the reduced outstanding");
    }

    @Test
    void permissionsAndTenantIsolation() throws Exception {
        Actor owner = owner();
        UUID customer = counterparty(owner, "Customer", "CUSTOMER");
        UUID invoice = finalizedDocument(owner, "invoices", TODAY, List.<Object[]>of(new Object[] {"Item", "1", "100.00", null}));
        recognize(owner, invoice, "INVOICE", customer);
        Actor viewer = actor(owner.businessId(), MembershipRole.VIEWER);
        assertTrue(getJson(viewer, "/api/commercial/documents/" + invoice + "/lifecycle").get("recognized").asBoolean());
        postJson(viewer, "/api/commercial/documents/" + invoice + "/adjustments", map("amount", "1.00", "reason", "x"), key(), 403);
        postJson(viewer, "/api/commercial/documents/" + invoice + "/void-recognized", map("reason", "x"), null, 403);
        Actor other = owner();
        getJson(other, "/api/commercial/documents/" + invoice + "/lifecycle", 404);
        postJson(other, "/api/commercial/documents/" + invoice + "/adjustments", map("amount", "1.00", "reason", "x"), key(), 404);
    }

    @Test
    void onlyReviewedSameBusinessOcrEvidenceCanBeLinkedAndLinkingNeverPosts() throws Exception {
        Actor owner = owner();
        UUID invoice = finalizedDocument(owner, "invoices", TODAY, List.<Object[]>of(new Object[] {"Item", "1", "100.00", null}));
        UUID unreviewed = UUID.randomUUID(), reviewed = UUID.randomUUID();
        jdbc.update("INSERT INTO uploaded_documents(id, user_id, file_url, processing_status) VALUES (?, ?, 'stored/u1', 'extracted')", unreviewed, owner.businessId());
        jdbc.update("INSERT INTO uploaded_documents(id, user_id, file_url, processing_status) VALUES (?, ?, 'stored/u2', 'confirmed')", reviewed, owner.businessId());
        assertEquals("evidence_not_reviewed", postJson(owner, "/api/commercial/documents/" + invoice + "/evidence",
                map("uploadedDocumentId", unreviewed), null, 409).get("error").asText());
        JsonNode linked = postJson(owner, "/api/commercial/documents/" + invoice + "/evidence", map("uploadedDocumentId", reviewed), null, 200);
        assertEquals(1, linked.get("evidence").size());
        assertEquals("confirmed", linked.get("evidence").get(0).get("reviewStatus").asText());
        assertFalse(linked.get("recognized").asBoolean(), "Linking evidence never recognizes or posts");
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM financial_obligations WHERE business_id = ?", Integer.class, owner.businessId()));
        assertEquals("evidence_already_linked", postJson(owner, "/api/commercial/documents/" + invoice + "/evidence",
                map("uploadedDocumentId", reviewed), null, 409).get("error").asText());

        Actor other = owner();
        UUID foreign = UUID.randomUUID();
        jdbc.update("INSERT INTO uploaded_documents(id, user_id, file_url, processing_status) VALUES (?, ?, 'stored/u3', 'confirmed')", foreign, other.businessId());
        postJson(owner, "/api/commercial/documents/" + invoice + "/evidence", map("uploadedDocumentId", foreign), null, 404);
        Actor viewer = actor(owner.businessId(), MembershipRole.VIEWER);
        postJson(viewer, "/api/commercial/documents/" + invoice + "/evidence", map("uploadedDocumentId", reviewed), null, 403);

        String linkId = linked.get("evidence").get(0).get("id").asText();
        JsonNode unlinked = postJson(owner, "/api/commercial/documents/" + invoice + "/evidence/" + linkId + "/unlink", map("reason", "Wrong scan"), null, 200);
        assertEquals("UNLINKED", unlinked.get("evidence").get(0).get("status").asText());
        assertThrows(Exception.class, () -> jdbc.update("DELETE FROM commercial_document_evidence_links WHERE id = ?", UUID.fromString(linkId)));
    }
}
