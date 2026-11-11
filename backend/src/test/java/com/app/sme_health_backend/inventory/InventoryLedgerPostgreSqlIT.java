package com.app.sme_health_backend.inventory;

import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.testsupport.ProductApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * F08 authoritative perpetual inventory on real PostgreSQL through the authenticated HTTP API:
 * frozen WAC policy f08-wac-v1, exact conservation, complete depletion, reversal symmetry, returns, concurrency,
 * idempotency, capitalization from a ledger payment, bill receipt, invoice fulfilment (COGS), project/sample
 * consumption, permissions, tenant isolation and database-level immutability.
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class InventoryLedgerPostgreSqlIT extends ProductApiTestBase {

    private JsonNode receipt(Actor a, UUID item, String qty, String value) throws Exception {
        return postJson(a, "/api/inventory/movements/receipts", map("itemId", item, "quantity", qty, "value", value), key(), 201);
    }

    private JsonNode issue(Actor a, UUID item, String qty, String purpose, int expected) throws Exception {
        return postJson(a, "/api/inventory/movements/issues", map("itemId", item, "quantity", qty, "purpose", purpose), key(), expected);
    }

    private JsonNode positionOf(Actor a, UUID item) throws Exception {
        for (JsonNode row : getJson(a, "/api/inventory/register").get("items")) {
            if (row.get("itemId").asText().equals(item.toString())) return row;
        }
        throw new AssertionError("item missing from register");
    }

    @Test
    void wacHalfUpConservationAndExactDepletion() throws Exception {
        Actor owner = owner();
        UUID item = item(owner, "WAC-1");
        postJson(owner, "/api/inventory/movements/opening", map("itemId", item, "quantity", "3", "value", "10.00"), key(), 201);
        JsonNode first = issue(owner, item, "1", "SALE", 201);
        assertEquals(0, new BigDecimal("-3.3333").compareTo(decimal(first, "valueDelta")));
        JsonNode second = issue(owner, item, "1", "SALE", 201);
        // 6.6667 * 1 / 2 = 3.33335 -> HALF_UP 3.3334 (frozen f08-wac-v1)
        assertEquals(0, new BigDecimal("-3.3334").compareTo(decimal(second, "valueDelta")));
        JsonNode last = issue(owner, item, "1", "SALE", 201);
        assertEquals(0, new BigDecimal("-3.3333").compareTo(decimal(last, "valueDelta")), "Depletion takes the exact residual");
        assertEquals("0", last.get("quantityAfter").asText());
        assertEquals(0, BigDecimal.ZERO.compareTo(decimal(last, "valueAfter")));
        assertEquals("f08-wac-v1", last.get("policyVersion").asText());

        JsonNode cogs = getJson(owner, "/api/inventory/cogs?from=" + TODAY.withDayOfMonth(1) + "&to=" + TODAY);
        assertEquals(0, new BigDecimal("10.00").compareTo(decimal(cogs, "costOfGoodsSold")), "No value created or lost");
        JsonNode error = issue(owner, item, "0.5", "SALE", 409);
        assertEquals("insufficient_stock", error.get("error").asText());
    }

    @Test
    void idempotencyReplayAndConflict() throws Exception {
        Actor owner = owner();
        UUID item = item(owner, "IDEM-1");
        String k = key();
        JsonNode a = postJson(owner, "/api/inventory/movements/receipts", map("itemId", item, "quantity", "5", "value", "50.00"), k, 201);
        JsonNode b = postJson(owner, "/api/inventory/movements/receipts", map("itemId", item, "quantity", "5", "value", "50.00"), k, 200);
        assertEquals(a.get("id").asText(), b.get("id").asText());
        JsonNode c = postJson(owner, "/api/inventory/movements/receipts", map("itemId", item, "quantity", "6", "value", "50.00"), k, 409);
        assertEquals("idempotency_key_reused", c.get("error").asText());
        assertEquals("5", positionOf(owner, item).get("quantity").asText());
        postJson(owner, "/api/inventory/movements/receipts", map("itemId", item, "quantity", "1", "value", "1.00"), null, 400);
        postJson(owner, "/api/inventory/movements/receipts", map("itemId", item, "quantity", "1", "value", "1.001"), key(), 400);
        postJson(owner, "/api/inventory/movements/receipts", map("itemId", item, "quantity", "-1", "value", "1.00"), key(), 400);
    }

    @Test
    void reversalIsExactlySymmetricAndBlockedWhenStockWasConsumed() throws Exception {
        Actor owner = owner();
        UUID item = item(owner, "REV-1");
        JsonNode r1 = receipt(owner, item, "4", "100.00");
        JsonNode i1 = issue(owner, item, "1", "INTERNAL", 201);
        JsonNode rev = postJson(owner, "/api/inventory/movements/" + i1.get("id").asText() + "/reverse", map("reason", "Entered twice"), key(), 201);
        assertEquals("REVERSAL", rev.get("movementType").asText());
        assertEquals("INTERNAL", rev.get("purpose").asText());
        assertEquals("4", rev.get("quantityAfter").asText());
        assertEquals(0, new BigDecimal("100.00").compareTo(decimal(rev, "valueAfter")), "Issue reversal restores exact value");
        assertEquals("already_reversed", postJson(owner, "/api/inventory/movements/" + i1.get("id").asText() + "/reverse",
                map("reason", "again"), key(), 409).get("error").asText());
        assertEquals("reversal_not_reversible", postJson(owner, "/api/inventory/movements/" + rev.get("id").asText() + "/reverse",
                map("reason", "again"), key(), 409).get("error").asText());
        postJson(owner, "/api/inventory/movements/" + i1.get("id").asText() + "/reverse", map("reason", " "), key(), 400);

        // Consume most stock, then reversing the original receipt would go negative -> blocked.
        issue(owner, item, "3", "SALE", 201);
        assertEquals("reversal_blocked_by_later_movements", postJson(owner, "/api/inventory/movements/"
                + r1.get("id").asText() + "/reverse", map("reason", "wrong receipt"), key(), 409).get("error").asText());
        JsonNode cogs = getJson(owner, "/api/inventory/cogs?from=" + TODAY.withDayOfMonth(1) + "&to=" + TODAY);
        assertEquals(0, new BigDecimal("75.00").compareTo(decimal(cogs, "costOfGoodsSold")));
        assertEquals(0, BigDecimal.ZERO.compareTo(decimal(cogs, "internalConsumption")), "Reversed internal issue nets to zero");
    }

    @Test
    void returnsTakeProportionalValueAndExactResidual() throws Exception {
        Actor owner = owner();
        UUID item = item(owner, "RET-1");
        receipt(owner, item, "3", "10.00");
        JsonNode sale = issue(owner, item, "3", "SALE", 201);
        String issueId = sale.get("id").asText();
        JsonNode r1 = postJson(owner, "/api/inventory/movements/returns", map("issueMovementId", issueId, "quantity", "1"), key(), 201);
        assertEquals(0, new BigDecimal("3.3333").compareTo(decimal(r1, "valueDelta")));
        assertEquals("SALE", r1.get("purpose").asText());
        JsonNode r2 = postJson(owner, "/api/inventory/movements/returns", map("issueMovementId", issueId, "quantity", "2"), key(), 201);
        assertEquals(0, new BigDecimal("6.6667").compareTo(decimal(r2, "valueDelta")), "Final return takes exact residual");
        assertEquals(0, new BigDecimal("10.00").compareTo(decimal(r2, "valueAfter")));
        assertEquals("return_exceeds_issue", postJson(owner, "/api/inventory/movements/returns",
                map("issueMovementId", issueId, "quantity", "0.1"), key(), 409).get("error").asText());
        assertEquals("issue_has_returns", postJson(owner, "/api/inventory/movements/" + issueId + "/reverse",
                map("reason", "x"), key(), 409).get("error").asText());
        JsonNode cogs = getJson(owner, "/api/inventory/cogs?from=" + TODAY.withDayOfMonth(1) + "&to=" + TODAY);
        assertEquals(0, BigDecimal.ZERO.compareTo(decimal(cogs, "costOfGoodsSold")), "Fully returned sale has zero COGS");
    }

    @Test
    void adjustmentsRequireReasonAndAreValuedByPolicy() throws Exception {
        Actor owner = owner();
        UUID item = item(owner, "ADJ-1");
        receipt(owner, item, "10", "100.00");
        postJson(owner, "/api/inventory/movements/adjustments", map("itemId", item, "direction", "DECREASE", "quantity", "1"), key(), 400);
        JsonNode out = postJson(owner, "/api/inventory/movements/adjustments", map("itemId", item, "direction", "DECREASE",
                "quantity", "2", "notes", "Damaged in storage"), key(), 201);
        assertEquals(0, new BigDecimal("-20.00").compareTo(decimal(out, "valueDelta")));
        postJson(owner, "/api/inventory/movements/adjustments", map("itemId", item, "direction", "DECREASE",
                "quantity", "1", "value", "5.00", "notes", "caller value not allowed"), key(), 400);
        postJson(owner, "/api/inventory/movements/adjustments", map("itemId", item, "direction", "INCREASE",
                "quantity", "1", "value", "12.00", "notes", "Found in count"), key(), 201);
        JsonNode cogs = getJson(owner, "/api/inventory/cogs?from=" + TODAY.withDayOfMonth(1) + "&to=" + TODAY);
        assertEquals(0, new BigDecimal("20.00").compareTo(decimal(cogs, "adjustmentLoss")));
        assertEquals(0, new BigDecimal("92.00").compareTo(decimal(positionOf(owner, item), "value")));
    }

    @Test
    void concurrentIssuesNeverOversellOrGoNegative() throws Exception {
        Actor owner = owner();
        UUID item = item(owner, "CONC-1");
        receipt(owner, item, "5", "50.00");
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit((Callable<Integer>) () -> {
                start.await();
                return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/inventory/movements/issues").cookie(owner.session()).with(csrf(owner.session()))
                                .header("Idempotency-Key", key()).contentType("application/json")
                                .content(json.writeValueAsString(map("itemId", item, "quantity", "1", "purpose", "SALE"))))
                        .andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        int created = 0, conflict = 0;
        for (Future<Integer> f : futures) {
            int s = f.get();
            if (s == 201) created++;
            else if (s == 409) conflict++;
            else fail("Unexpected status " + s);
        }
        pool.shutdown();
        assertEquals(5, created);
        assertEquals(5, conflict);
        JsonNode p = positionOf(owner, item);
        assertEquals("0", p.get("quantity").asText());
        assertEquals(0, BigDecimal.ZERO.compareTo(decimal(p, "value")));
        assertEquals(6, jdbc.queryForObject("SELECT count(*) FROM inventory_movements WHERE item_id = ?", Integer.class, item));
        BigDecimal sum = jdbc.queryForObject("SELECT sum(value_delta) FROM inventory_movements WHERE item_id = ?", BigDecimal.class, item);
        assertEquals(0, BigDecimal.ZERO.compareTo(sum), "Sum of deltas equals the (zero) position");
    }

    @Test
    void capitalizationFromLedgerPaymentIsBoundedAndBillReceiptIsOnce() throws Exception {
        Actor owner = owner();
        UUID account = account(owner, "100000.00");
        UUID item = item(owner, "CAP-1");
        UUID payment = postTransaction(owner, account, "OPERATING_EXPENSE_PAYMENT", "500.00", TODAY);
        postJson(owner, "/api/inventory/movements/receipts", map("itemId", item, "quantity", "3", "value", "300.00",
                "transactionId", payment), key(), 201);
        assertEquals("capitalization_exceeds_payment", postJson(owner, "/api/inventory/movements/receipts",
                map("itemId", item, "quantity", "3", "value", "300.00", "transactionId", payment), key(), 409).get("error").asText());
        UUID sale = postTransaction(owner, account, "DIRECT_SALE_RECEIPT", "50.00", TODAY);
        assertEquals("transaction_not_capitalizable", postJson(owner, "/api/inventory/movements/receipts",
                map("itemId", item, "quantity", "1", "value", "10.00", "transactionId", sale), key(), 409).get("error").asText());

        UUID bill = finalizedDocument(owner, "bills", TODAY, List.<Object[]>of(new Object[] {"Stock", "4", "25.50", item},
                new Object[] {"Freight", "1", "40.00", null}));
        String k = key();
        JsonNode received = postJson(owner, "/api/inventory/documents/bills/" + bill + "/receive", map(), k, 201);
        assertEquals(1, received.get("movements").size(), "Only inventory-linked lines are capitalized");
        assertEquals(0, new BigDecimal("102.00").compareTo(decimal(received.get("movements").get(0), "valueDelta")));
        assertEquals("already_received", postJson(owner, "/api/inventory/documents/bills/" + bill + "/receive", map(), key(), 409)
                .get("error").asText());
        assertEquals(0, new BigDecimal("402.00").compareTo(decimal(positionOf(owner, item), "value")));
    }

    @Test
    void invoiceFulfilmentProjectAndSampleConsumptionUseOneAuthority() throws Exception {
        Actor owner = owner();
        UUID item = item(owner, "FUL-1");
        receipt(owner, item, "10", "200.00");
        UUID invoice = finalizedDocument(owner, "invoices", TODAY, List.<Object[]>of(new Object[] {"Goods", "2", "70.00", item}));
        JsonNode fulfil = postJson(owner, "/api/inventory/documents/invoices/" + invoice + "/fulfil", map(), key(), 201);
        assertEquals("SALE", fulfil.get("movements").get(0).get("purpose").asText());
        UUID projectId = project(owner, "PRJ-INV");
        postJson(owner, "/api/inventory/movements/issues", map("itemId", item, "quantity", "3", "purpose", "PROJECT",
                "projectId", projectId), key(), 201);
        postJson(owner, "/api/inventory/movements/issues", map("itemId", item, "quantity", "1", "purpose", "PROJECT"), key(), 400);
        postJson(owner, "/api/inventory/movements/issues", map("itemId", item, "quantity", "1", "purpose", "SAMPLE",
                "sampleId", UUID.randomUUID()), key(), 404);
        JsonNode cogs = getJson(owner, "/api/inventory/cogs?from=" + TODAY.withDayOfMonth(1) + "&to=" + TODAY);
        assertEquals(0, new BigDecimal("40.00").compareTo(decimal(cogs, "costOfGoodsSold")));
        assertEquals(0, new BigDecimal("60.00").compareTo(decimal(cogs, "projectMaterial")));
        JsonNode docMovements = getJson(owner, "/api/inventory/documents/" + invoice + "/movements");
        assertEquals(1, docMovements.get("movements").size());
    }

    @Test
    void permissionsTenantIsolationAndDatabaseImmutability() throws Exception {
        Actor owner = owner();
        UUID item = item(owner, "SEC-1");
        JsonNode r = receipt(owner, item, "2", "20.00");
        for (MembershipRole role : List.of(MembershipRole.VIEWER, MembershipRole.MANAGER)) {
            Actor reader = actor(owner.businessId(), role);
            getJson(reader, "/api/inventory/register");
            postJson(reader, "/api/inventory/movements/issues", map("itemId", item, "quantity", "1", "purpose", "SALE"), key(), 403);
        }
        Actor accountant = actor(owner.businessId(), MembershipRole.ACCOUNTANT);
        issue(accountant, item, "1", "SALE", 201);

        Actor other = owner();
        postJson(other, "/api/inventory/movements/issues", map("itemId", item, "quantity", "1", "purpose", "SALE"), key(), 404);
        postJson(other, "/api/inventory/movements/" + r.get("id").asText() + "/reverse", map("reason", "x"), key(), 404);
        for (JsonNode row : getJson(other, "/api/inventory/register").get("items")) {
            assertNotEquals(item.toString(), row.get("itemId").asText());
        }

        assertThrows(Exception.class, () -> jdbc.update("UPDATE inventory_movements SET value_delta = 0 WHERE item_id = ?", item));
        assertThrows(Exception.class, () -> jdbc.update("DELETE FROM inventory_movements WHERE item_id = ?", item));
        assertThrows(Exception.class, () -> jdbc.update("UPDATE inventory_positions SET value = 999 WHERE item_id = ?", item));
        assertThrows(Exception.class, () -> jdbc.update("DELETE FROM inventory_positions WHERE item_id = ?", item));
    }
}
