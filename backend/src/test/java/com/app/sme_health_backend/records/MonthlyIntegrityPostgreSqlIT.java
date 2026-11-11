package com.app.sme_health_backend.records;

import com.app.sme_health_backend.documents.dto.DocumentConfirmationRequest;
import com.app.sme_health_backend.documents.entity.UploadedDocument;
import com.app.sme_health_backend.documents.exception.DocumentAlreadyConfirmedException;
import com.app.sme_health_backend.documents.processing.DocumentStatus;
import com.app.sme_health_backend.documents.repository.UploadedDocumentRepository;
import com.app.sme_health_backend.documents.service.DocumentConfirmationService;
import com.app.sme_health_backend.identity.entity.*;
import com.app.sme_health_backend.identity.model.*;
import com.app.sme_health_backend.identity.repository.*;
import com.app.sme_health_backend.profile.entity.BusinessProfile;
import com.app.sme_health_backend.profile.repository.BusinessProfileRepository;
import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.records.repository.MonthlyRecordRepository;
import com.app.sme_health_backend.records.service.MonthlyRecordService;
import com.app.sme_health_backend.scoring.service.ScoringService;
import com.app.sme_health_backend.testsupport.DisposablePostgres;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integrity of the transaction-driven monthly model (V52, monthly-projection-v1) on committed PostgreSQL transactions,
 * real sessions/CSRF and distinct identities: no manual monthly write path, evidence-only document confirmation,
 * serialized idempotent projection under concurrency, compare-and-clear invalidation, optimistic financing inputs.
 */
@SpringBootTest(properties = {"app.ai.enabled=false", "app.ai.live-activation-approved=false", "app.ai.api-key="})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class MonthlyIntegrityPostgreSqlIT extends DisposablePostgres {
    @Autowired AppUserRepository users;
    @Autowired BusinessRepository businesses;
    @Autowired BusinessMembershipRepository memberships;
    @Autowired BusinessProfileRepository profiles;
    @Autowired MonthlyRecordRepository records;
    @Autowired UploadedDocumentRepository documents;
    @Autowired MonthlyRecordService monthly;
    @Autowired DocumentConfirmationService confirmation;
    @Autowired ScoringService scoring;
    @Autowired com.app.sme_health_backend.records.derived.MonthlyProjectionService projections;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired tools.jackson.databind.ObjectMapper json;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
    static final String MONTH = "2026-10", PASSWORD = "Pilot-synthetic-password-2026!";
    record Fixture(AppUser user, UUID businessId) { }

    Fixture fixture() { return fixture(MembershipRole.OWNER); }
    Fixture fixture(MembershipRole role) {
        AppUser user = new AppUser(); user.setEmail("pilot." + UUID.randomUUID() + "@example.com");
        user.setFullName("Synthetic pilot actor"); user.setPasswordHash(passwords.encode(PASSWORD));
        user.setAccountStatus(AccountStatus.ACTIVE); user.setMustChangePassword(false); user = users.saveAndFlush(user);
        UUID business = UUID.randomUUID(); businesses.saveAndFlush(new Business(business));
        memberships.saveAndFlush(new BusinessMembership(user.getId(), business, role, MembershipStatus.ACTIVE));
        BusinessProfile profile = new BusinessProfile(); profile.setUserId(business); profile.setBusinessType("services");
        profile.setLanguagePreference("en");
        // Known synthetic repayment evidence also makes zero-revenue/C0GS-only fixtures scorable.
        profile.setPaymentBehavior("immediate"); profiles.saveAndFlush(profile);
        assertNotEquals(user.getId(), business);
        return new Fixture(user, business);
    }
    Cookie login(Fixture fixture) throws Exception {
        var csrf = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
        Cookie xsrf = csrf.getCookie("XSRF-TOKEN"), initial = csrf.getCookie("FINSIGHT_SESSION"); assertNotNull(xsrf);
        var request = post("/api/auth/login").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", fixture.user.getEmail(), "password", PASSWORD)));
        if (initial != null) request.cookie(initial);
        var session = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getCookie("FINSIGHT_SESSION");
        assertNotNull(session);
        mvc.perform(post("/api/businesses/active").cookie(session).with(csrf(session)).contentType("application/json")
                .content(json.writeValueAsString(Map.of("businessId", fixture.businessId)))).andExpect(status().isOk());
        return session;
    }
    RequestPostProcessor csrf(Cookie session) throws Exception {
        var request = get("/api/auth/csrf"); if (session != null) request.cookie(session);
        var response = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse();
        var xsrf = response.getCookie("XSRF-TOKEN"); assertNotNull(xsrf);
        var token = json.readValue(response.getContentAsString(), com.app.sme_health_backend.identity.dto.CsrfResponse.class);
        return servletRequest -> {
            var cookies = new ArrayList<>(Arrays.asList(servletRequest.getCookies() == null ? new Cookie[0] : servletRequest.getCookies()));
            cookies.removeIf(cookie -> cookie.getName().equals(xsrf.getName())); cookies.add(xsrf);
            servletRequest.setCookies(cookies.toArray(Cookie[]::new)); servletRequest.addHeader(token.headerName(), token.token()); return servletRequest;
        };
    }
    ResultActions save(Cookie session, Map<String, Object> body) throws Exception {
        return mvc.perform(post("/api/records/monthly").cookie(session).with(csrf(session)).contentType("application/json").content(json.writeValueAsString(body)));
    }
    ResultActions confirm(Cookie session, UUID document, DocumentConfirmationRequest body) throws Exception {
        return mvc.perform(post("/api/documents/" + document + "/confirm").cookie(session).with(csrf(session))
                .contentType("application/json").content(json.writeValueAsString(body)));
    }
    Map<String, Object> body(String month, Long version) {
        Map<String, Object> body = new LinkedHashMap<>(); body.put("month", month); body.put("expectedVersion", version);
        for (String field : List.of("cashInflow", "cashOutflow", "revenue", "operatingExpenses", "cashBalanceEom")) body.put(field, new BigDecimal("100.00"));
        body.put("cogs", null); body.put("financingType", "none"); return body;
    }
    MonthlyRecord seed(Fixture f, String month) {
        MonthlyRecord record = new MonthlyRecord(); record.setUserId(f.businessId); record.setMonth(month);
        record.setRevenue(new BigDecimal("100.00")); record.setOperatingExpenses(new BigDecimal("20.00"));
        record.setCashInflow(new BigDecimal("100.00")); record.setCashOutflow(new BigDecimal("20.00"));
        record.setCashBalanceEom(new BigDecimal("77.00")); record.setReceivablesOutstanding(new BigDecimal("12.00"));
        record.setUpdatedAt(LocalDateTime.now()); return records.saveAndFlush(record);
    }
    UUID document(Fixture f) {
        UploadedDocument document = new UploadedDocument(); document.setId(UUID.randomUUID()); document.setUserId(f.businessId);
        document.setFileUrl("/synthetic/pilot.pdf"); document.setProcessingStatus(DocumentStatus.extracted);
        document.setExtractedData("{\"fixture\":true}"); documents.saveAndFlush(document); return document.getId();
    }
    DocumentConfirmationRequest request(String amount, String party, String classification, String flow, boolean override) {
        return new DocumentConfirmationRequest(MONTH, new BigDecimal(amount), LocalDate.of(2026, 10, 15), party,
                classification, flow, new BigDecimal("77.00"), override);
    }
    UploadedDocument apply(Fixture f, UUID document, DocumentConfirmationRequest request) {
        return confirmation.confirmDocument(f.businessId, document, f.user.getId(), request);
    }
    MonthlyRecord current(Fixture f) { return records.findByUserIdAndMonth(f.businessId, MONTH).orElseThrow(); }
    int count(Fixture f, String table) {
        String scope = table.equals("document_financial_contributions") || table.equals("security_audit_events") ? "business_id" : "user_id";
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + scope + "=?", Integer.class, f.businessId);
    }
    Map<String, String> snapshot(Fixture f) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String table : List.of("monthly_records", "score_results", "uploaded_documents", "document_financial_contributions", "security_audit_events")) {
            String scope = table.equals("document_financial_contributions") || table.equals("security_audit_events") ? "business_id" : "user_id";
            result.put(table, jdbc.queryForObject("SELECT COALESCE(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text),'[]'::jsonb)::text FROM "
                    + table + " t WHERE " + scope + "=?", String.class, f.businessId));
        }
        return result;
    }
    void amount(String expected, BigDecimal actual) { assertEquals(0, new BigDecimal(expected).compareTo(actual)); }
    void audit(Fixture f, String event, String target) {
        var rows = jdbc.queryForList("SELECT actor_user_id,business_id,is_system FROM security_audit_events WHERE event_type=? AND target_id=?", event, target);
        assertEquals(1, rows.size()); assertEquals(f.user.getId(), rows.getFirst().get("actor_user_id"));
        assertEquals(f.businessId, rows.getFirst().get("business_id")); assertEquals(false, rows.getFirst().get("is_system"));
    }

    UUID account(Cookie session, String openingDate) throws Exception {
        String body = mvc.perform(post("/api/finance/accounts").cookie(session).with(csrf(session)).header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("accountName", "Bank " + UUID.randomUUID().toString().substring(0, 6), "accountType", "BANK",
                        "currency", "PKR", "openingBalance", "1000.00", "openingDate", openingDate))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("id").asText());
    }
    void transaction(Cookie session, UUID account, String type, String amount, String date) throws Exception {
        mvc.perform(post("/api/finance/transactions").cookie(session).with(csrf(session)).header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("accountId", account, "eventType", type, "amount", amount,
                        "effectiveDate", date, "description", "Integrity " + type)))).andExpect(status().isCreated());
    }
    java.time.YearMonth thisMonth() { return java.time.YearMonth.now(java.time.ZoneId.of("Asia/Karachi")); }

    @Test void manualMonthlyWritesAreRefusedForWritersAndForbiddenForReadersWithoutAnyMutation() throws Exception {
        for (MembershipRole role : List.of(MembershipRole.OWNER, MembershipRole.ACCOUNTANT)) {
            var f = fixture(role); var session = login(f); var before = snapshot(f);
            save(session, body(MONTH, null)).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("monthly_figures_derived"));
            save(session, body(MONTH, 0L)).andExpect(status().isConflict());
            var after = snapshot(f);
            assertEquals(before.get("monthly_records"), after.get("monthly_records"), role + " manual write must not create a month");
            assertEquals(before.get("score_results"), after.get("score_results"), role + " manual write must not score");
        }
        for (MembershipRole role : List.of(MembershipRole.MANAGER, MembershipRole.VIEWER)) {
            var f = fixture(role); var session = login(f);
            save(session, body(MONTH, null)).andExpect(status().isForbidden());
            mvc.perform(post("/api/records/monthly/recalculate").cookie(session).with(csrf(session))).andExpect(status().isForbidden());
        }
    }

    @Test void documentConfirmationIsEvidenceOnlyAndAppliesExactlyOnce() throws Exception {
        var f = fixture(); seed(f, MONTH); var before = current(f); UUID document = document(f);
        apply(f, document, request("1500.00", "Vendor", "revenue", "cash_inflow", false));
        var after = current(f);
        amount(before.getRevenue().toPlainString(), after.getRevenue());
        amount(before.getCashInflow().toPlainString(), after.getCashInflow());
        assertEquals(before.getVersion(), after.getVersion(), "a confirmed scan never writes the month");
        assertEquals(0, count(f, "document_financial_contributions"));
        assertThrows(DocumentAlreadyConfirmedException.class, () -> apply(f, document, request("1500.00", "Vendor", "revenue", "cash_inflow", true)));
    }

    @Test void concurrentRefreshesSerializeIdempotentlyAndNeverDuplicateMonths() throws Exception {
        var f = fixture(); var session = login(f);
        java.time.YearMonth m0 = thisMonth(), m2 = m0.minusMonths(2);
        UUID bank = account(session, m2.atDay(1).toString());
        transaction(session, bank, "DIRECT_SALE_RECEIPT", "400.00", m2.atDay(5).toString());
        transaction(session, bank, "OPERATING_EXPENSE_PAYMENT", "150.00", m0.minusMonths(1).atDay(7).toString());
        var pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<com.app.sme_health_backend.records.derived.MonthlyProjectionService.RefreshResult>> runs = new ArrayList<>();
            for (int i = 0; i < 4; i++) runs.add(pool.submit(() -> projections.refresh(f.businessId)));
            int writers = 0;
            for (var run : runs) if (!run.get(60, TimeUnit.SECONDS).changedMonths().isEmpty()) writers++;
            assertEquals(1, writers, "exactly one serialized refresh writes; the others find identical fingerprints");
        } finally { pool.shutdownNow(); }
        assertEquals(3, count(f, "monthly_records"));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(DISTINCT month) FROM monthly_records WHERE user_id=? AND source='DERIVED'",
                Integer.class, f.businessId));
        amount("400.00", records.findByUserIdAndMonth(f.businessId, m2.toString()).orElseThrow().getRevenue());
        amount("1250.00", records.findByUserIdAndMonth(f.businessId, m0.toString()).orElseThrow().getCashBalanceEom());
        assertFalse(projections.status(f.businessId).stale());
        var stable = snapshot(f);
        assertTrue(projections.refresh(f.businessId).changedMonths().isEmpty());
        var again = snapshot(f);
        assertEquals(stable.get("monthly_records"), again.get("monthly_records"), "rebuild without source change is byte-identical");
        assertEquals(stable.get("score_results"), again.get("score_results"), "rebuild without source change does not rescore");
    }

    @Test void sourceWritesInvalidateOnlyTheirTenantAndRefreshClearsWithCompareAndSet() throws Exception {
        var a = fixture(); var sa = login(a); var b = fixture(); var sb = login(b);
        UUID bankA = account(sa, thisMonth().atDay(1).toString()); account(sb, thisMonth().atDay(1).toString());
        projections.refresh(a.businessId); projections.refresh(b.businessId);
        long seqB = jdbc.queryForObject("SELECT dirty_seq FROM monthly_projection_state WHERE business_id=?", Long.class, b.businessId);
        transaction(sa, bankA, "DIRECT_SALE_RECEIPT", "10.00", LocalDate.now(java.time.ZoneId.of("Asia/Karachi")).toString());
        assertTrue(projections.status(a.businessId).dirty());
        assertFalse(projections.status(b.businessId).dirty());
        assertEquals(seqB, jdbc.queryForObject("SELECT dirty_seq FROM monthly_projection_state WHERE business_id=?", Long.class, b.businessId));
        projections.refreshIfStale(a.businessId);
        assertFalse(projections.status(a.businessId).dirty());
        amount("10.00", records.findByUserIdAndMonth(a.businessId, thisMonth().toString()).orElseThrow().getRevenue());
        amount("0.00", records.findByUserIdAndMonth(b.businessId, thisMonth().toString()).orElseThrow().getRevenue());
    }

    @Test void unscorableMonthsNeverPoisonTheProjectionTransaction() throws Exception {
        var f = fixture(); var session = login(f);
        profiles.findById(f.businessId).ifPresent(p -> { p.setPaymentBehavior(null); profiles.saveAndFlush(p); });
        account(session, thisMonth().atDay(1).toString());
        var result = projections.refresh(f.businessId);
        assertTrue(result.clean());
        assertTrue(result.rescoredMonths().isEmpty(), "zero activity and no repayment evidence: nothing can be scored");
        assertEquals(1, count(f, "monthly_records"));
        assertFalse(projections.status(f.businessId).stale());
        assertNull(projections.status(f.businessId).lastError());
    }

    // ---------------------------------------------------------------- read-path freshness (FreshnessOutcome)

    static final com.app.sme_health_backend.records.derived.MonthlyProjectionService.FreshnessOutcome
            FRESH = com.app.sme_health_backend.records.derived.MonthlyProjectionService.FreshnessOutcome.FRESH,
            COALESCED = com.app.sme_health_backend.records.derived.MonthlyProjectionService.FreshnessOutcome.COALESCED,
            REBUILT = com.app.sme_health_backend.records.derived.MonthlyProjectionService.FreshnessOutcome.REBUILT,
            FAILED = com.app.sme_health_backend.records.derived.MonthlyProjectionService.FreshnessOutcome.FAILED;

    /** Superuser connection holding the business's projection advisory lock until rolled back (an in-flight rebuild). */
    java.sql.Connection holdProjectionLock(UUID businessId) throws Exception {
        java.sql.Connection c = DisposablePostgres.POSTGRES.getPostgresDatabase().getConnection();
        c.setAutoCommit(false);
        try (var st = c.prepareStatement("SELECT pg_advisory_xact_lock(hashtext(?))")) {
            st.setString(1, "monthly-projection:" + businessId);
            st.execute();
        }
        return c;
    }
    void awaitAdvisoryWaiters(int expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbc.queryForObject("SELECT COUNT(*) FROM pg_locks WHERE locktype='advisory' AND NOT granted", Integer.class);
            if (waiting != null && waiting >= expected) return;
            Thread.sleep(25);
        }
        fail("expected " + expected + " requests waiting on the projection lock");
    }
    Map<String, Long> versions(Fixture f) {
        Map<String, Long> out = new TreeMap<>();
        for (var row : jdbc.queryForList("SELECT month, version FROM monthly_records WHERE user_id=?", f.businessId))
            out.put(((String) row.get("month")).trim(), ((Number) row.get("version")).longValue());
        return out;
    }

    @Test void freshReadsTakeNoLockAndWriteNothingWhileStaleReadsRebuildOnlyChangedMonths() throws Exception {
        var f = fixture(); var session = login(f);
        java.time.YearMonth m0 = thisMonth(), m1 = m0.minusMonths(1), m2 = m0.minusMonths(2);
        UUID bank = account(session, m2.atDay(1).toString());
        transaction(session, bank, "DIRECT_SALE_RECEIPT", "400.00", m2.atDay(5).toString());
        assertEquals(REBUILT, projections.refreshIfStale(f.businessId));
        var stable = snapshot(f); var before = versions(f);
        assertEquals(3, before.size());

        // FRESH is answered by the state lookup alone: it returns even while another session holds the business lock.
        try (var holder = holdProjectionLock(f.businessId)) {
            var pool = Executors.newSingleThreadExecutor();
            try { assertEquals(FRESH, pool.submit(() -> projections.refreshIfStale(f.businessId)).get(10, TimeUnit.SECONDS)); }
            finally { pool.shutdownNow(); holder.rollback(); }
        }
        assertEquals(stable, snapshot(f), "a fresh read rewrites no month and no score");

        // REBUILT: a current-month entry rewrites only the current month; earlier months keep their version.
        transaction(session, bank, "OPERATING_EXPENSE_PAYMENT", "150.00", LocalDate.now(java.time.ZoneId.of("Asia/Karachi")).toString());
        assertEquals(REBUILT, projections.refreshIfStale(f.businessId));
        var after = versions(f);
        assertEquals(before.get(m2.toString()), after.get(m2.toString()));
        assertEquals(before.get(m1.toString()), after.get(m1.toString()));
        assertEquals(before.get(m0.toString()) + 1, after.get(m0.toString()));
        amount("150.00", records.findByUserIdAndMonth(f.businessId, m0.toString()).orElseThrow().getCashOutflow());
        assertEquals(FRESH, projections.refreshIfStale(f.businessId));
    }

    @Test void simultaneousStaleReadsForOneBusinessCoalesceIntoExactlyOneRebuild() throws Exception {
        var f = fixture(); var session = login(f);
        java.time.YearMonth m0 = thisMonth(), m1 = m0.minusMonths(1), m2 = m0.minusMonths(2);
        UUID bank = account(session, m2.atDay(1).toString());
        transaction(session, bank, "DIRECT_SALE_RECEIPT", "400.00", m2.atDay(5).toString());
        projections.refresh(f.businessId);
        var before = versions(f);
        // Backdated entry: m1 and (cash carried forward) m0 change; m2 does not.
        transaction(session, bank, "DIRECT_SALE_RECEIPT", "250.00", m1.atDay(9).toString());
        assertTrue(projections.status(f.businessId).stale());

        int readers = 5;
        var pool = Executors.newFixedThreadPool(readers);
        List<Future<com.app.sme_health_backend.records.derived.MonthlyProjectionService.FreshnessOutcome>> runs = new ArrayList<>();
        try (var holder = holdProjectionLock(f.businessId)) {
            for (int i = 0; i < readers; i++) runs.add(pool.submit(() -> projections.refreshIfStale(f.businessId)));
            awaitAdvisoryWaiters(readers);   // every reader saw "stale" and is queued on the same business lock
            holder.rollback();
        }
        Map<Object, Integer> outcomes = new HashMap<>();
        try { for (var run : runs) outcomes.merge(run.get(60, TimeUnit.SECONDS), 1, Integer::sum); }
        finally { pool.shutdownNow(); }
        assertEquals(Map.of(REBUILT, 1, COALESCED, readers - 1), outcomes);

        var after = versions(f);
        assertEquals(before.get(m2.toString()), after.get(m2.toString()), "unchanged month is not rewritten");
        assertEquals(before.get(m1.toString()) + 1, after.get(m1.toString()), "changed month written exactly once");
        assertEquals(before.get(m0.toString()) + 1, after.get(m0.toString()), "changed month written exactly once");
        assertEquals(3, count(f, "monthly_records"));
        amount("250.00", records.findByUserIdAndMonth(f.businessId, m1.toString()).orElseThrow().getRevenue());
        amount("1650.00", records.findByUserIdAndMonth(f.businessId, m0.toString()).orElseThrow().getCashBalanceEom());
        assertFalse(projections.status(f.businessId).stale());
    }

    @Test void staleReadsForDifferentBusinessesNeverWaitOnEachOther() throws Exception {
        var a = fixture(); var sa = login(a); var b = fixture(); var sb = login(b);
        String today = LocalDate.now(java.time.ZoneId.of("Asia/Karachi")).toString();
        UUID bankA = account(sa, thisMonth().atDay(1).toString()); UUID bankB = account(sb, thisMonth().atDay(1).toString());
        transaction(sa, bankA, "DIRECT_SALE_RECEIPT", "111.00", today);
        transaction(sb, bankB, "DIRECT_SALE_RECEIPT", "222.00", today);
        var pool = Executors.newFixedThreadPool(2);
        try (var holder = holdProjectionLock(a.businessId)) {
            var pendingA = pool.submit(() -> projections.refreshIfStale(a.businessId));
            awaitAdvisoryWaiters(1);
            // B rebuilds to completion while A is blocked: the lock is per business, never global.
            assertEquals(REBUILT, pool.submit(() -> projections.refreshIfStale(b.businessId)).get(30, TimeUnit.SECONDS));
            assertFalse(pendingA.isDone(), "A is still queued behind its own business lock");
            assertTrue(projections.status(a.businessId).stale());
            holder.rollback();
            assertEquals(REBUILT, pendingA.get(60, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
        String month = thisMonth().toString();
        amount("111.00", records.findByUserIdAndMonth(a.businessId, month).orElseThrow().getRevenue());
        amount("222.00", records.findByUserIdAndMonth(b.businessId, month).orElseThrow().getRevenue());
        assertFalse(projections.status(a.businessId).stale()); assertFalse(projections.status(b.businessId).stale());
    }

    @Test void failedRebuildStaysDirtyRecordsTheErrorAndTheNextReadRecovers() throws Exception {
        var f = fixture(); var session = login(f);
        UUID bank = account(session, thisMonth().atDay(1).toString());
        transaction(session, bank, "DIRECT_SALE_RECEIPT", "75.00", LocalDate.now(java.time.ZoneId.of("Asia/Karachi")).toString());
        String fn = "fail_projection_" + f.businessId.toString().replace("-", "");
        try (var admin = DisposablePostgres.POSTGRES.getPostgresDatabase().getConnection(); var st = admin.createStatement()) {
            st.execute("CREATE FUNCTION " + fn + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN "
                    + "IF NEW.user_id = '" + f.businessId + "' THEN RAISE EXCEPTION 'injected projection failure'; END IF; "
                    + "RETURN NEW; END $$");
            st.execute("CREATE TRIGGER " + fn + " BEFORE INSERT OR UPDATE ON monthly_records FOR EACH ROW EXECUTE FUNCTION " + fn + "()");
            try {
                assertEquals(FAILED, projections.refreshIfStale(f.businessId));
                var state = projections.status(f.businessId);
                assertTrue(state.dirty(), "a failed rebuild never clears the dirty flag");
                assertTrue(state.stale());
                assertNotNull(state.lastError(), "the failure is recorded");
                assertEquals(0, count(f, "monthly_records"), "the failed rebuild rolled back completely");
                assertEquals(FAILED, projections.refreshIfStale(f.businessId), "still failing: retried, not skipped");
            } finally {
                st.execute("DROP TRIGGER " + fn + " ON monthly_records");
                st.execute("DROP FUNCTION " + fn + "()");
            }
        }
        assertEquals(REBUILT, projections.refreshIfStale(f.businessId), "the next read retries and succeeds");
        var state = projections.status(f.businessId);
        assertFalse(state.stale()); assertNull(state.lastError());
        amount("75.00", records.findByUserIdAndMonth(f.businessId, thisMonth().toString()).orElseThrow().getRevenue());
        assertEquals(FRESH, projections.refreshIfStale(f.businessId));
    }

    @Test void upgradedLegacyMonthsArePreservedOutsideCoverageAndReplacedByDerivedMonthsInside() throws Exception {
        var f = fixture(); var session = login(f);
        java.time.YearMonth m0 = thisMonth(), m1 = m0.minusMonths(1), m4 = m0.minusMonths(4);
        // State as V52 leaves an upgraded business: pre-V52 manual months labelled LEGACY_MANUAL (column default) and
        // their financing facts copied to monthly_financing_inputs with origin LEGACY_MIGRATION.
        jdbc.update("INSERT INTO monthly_records(user_id,month,cash_inflow,cash_outflow,revenue,operating_expenses,cash_balance_eom,loan_outstanding,interest_expense,financing_type) "
                + "VALUES (?,?,7000.00,3000.00,8000.00,2500.00,40000.00,9000.00,150.00,'islamic')", f.businessId, m4.toString());
        jdbc.update("INSERT INTO monthly_records(user_id,month,cash_inflow,cash_outflow,revenue,operating_expenses,cash_balance_eom,loan_outstanding,interest_expense,financing_type) "
                + "VALUES (?,?,9999.00,1111.00,9999.00,1111.00,99999.00,5000.00,75.00,'conventional')", f.businessId, m1.toString());
        jdbc.update("INSERT INTO monthly_financing_inputs(business_id,month,loan_outstanding,interest_expense,financing_type,origin) VALUES (?,?,5000.00,75.00,'conventional','LEGACY_MIGRATION')",
                f.businessId, m1.toString());
        String legacyBefore = jdbc.queryForObject("SELECT to_jsonb(t)::text FROM monthly_records t WHERE user_id=? AND month=?", String.class, f.businessId, m4.toString());

        UUID bank = account(session, m1.atDay(1).toString());
        transaction(session, bank, "DIRECT_SALE_RECEIPT", "300.00", m1.atDay(4).toString());
        projections.refresh(f.businessId);

        assertEquals(legacyBefore, jdbc.queryForObject("SELECT to_jsonb(t)::text FROM monthly_records t WHERE user_id=? AND month=?", String.class, f.businessId, m4.toString()),
                "a legacy month before ledger coverage is preserved exactly, still labelled LEGACY_MANUAL");
        assertTrue(legacyBefore.contains("\"source\": \"LEGACY_MANUAL\""), legacyBefore);
        MonthlyRecord derived = records.findByUserIdAndMonth(f.businessId, m1.toString()).orElseThrow();
        assertTrue(derived.isDerived(), "a legacy month inside coverage is replaced by the derived month (one truth per month)");
        amount("300.00", derived.getRevenue());
        amount("300.00", derived.getCashInflow());
        amount("1300.00", derived.getCashBalanceEom());
        amount("5000.00", derived.getLoanOutstanding());
        amount("75.00", derived.getInterestExpense());
        assertEquals("conventional", derived.getFinancingType());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM monthly_records WHERE user_id=? AND month=?", Integer.class, f.businessId, m1.toString()));
        assertEquals(List.of(m4.toString(), m1.toString(), m0.toString()), new ArrayList<>(versions(f).keySet()));
    }

    @Test void supplementalFinancingUsesOptimisticVersionsAndValidation() throws Exception {
        var f = fixture(); var session = login(f);
        String path = "/api/records/monthly/financing/" + MONTH;
        mvc.perform(put(path).cookie(session).with(csrf(session)).contentType("application/json")
                .content(json.writeValueAsString(Map.of("loanOutstanding", "5000.00", "financingType", "islamic"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0));
        mvc.perform(put(path).cookie(session).with(csrf(session)).contentType("application/json")
                .content(json.writeValueAsString(Map.of("loanOutstanding", "6000.00"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("stale_financing_input"));
        mvc.perform(put(path).cookie(session).with(csrf(session)).contentType("application/json")
                .content(json.writeValueAsString(Map.of("loanOutstanding", "6000.00", "financingType", "islamic", "expectedVersion", 0))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        for (String bad : List.of("-1.00", "1.005", "abc", "1000000000000.00"))
            mvc.perform(put(path).cookie(session).with(csrf(session)).contentType("application/json")
                    .content(json.writeValueAsString(Map.of("loanOutstanding", bad, "expectedVersion", 1))))
                    .andExpect(status().isBadRequest());
        mvc.perform(put(path).cookie(session).with(csrf(session)).contentType("application/json")
                .content(json.writeValueAsString(Map.of("financingType", "hybrid", "expectedVersion", 1)))).andExpect(status().isBadRequest());
        assertEquals("6000.00", jdbc.queryForObject("SELECT loan_outstanding::text FROM monthly_financing_inputs WHERE business_id=? AND month=?",
                String.class, f.businessId, MONTH));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM security_audit_events WHERE business_id=? AND event_type='MONTHLY_FINANCING_INPUT_UPDATED' AND actor_user_id=?",
                Integer.class, f.businessId, f.user.getId()));
    }
}
