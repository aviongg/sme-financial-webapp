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

/** Committed real PostgreSQL transactions, real sessions/CSRF and distinct human/business identities. */
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
    @MockitoSpyBean ScoringService scoring;
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

    @Test void ownerAndAccountantMonthlyWritesUseRealActorAndReturnCommittedVersions() throws Exception {
        for (MembershipRole role : List.of(MembershipRole.OWNER, MembershipRole.ACCOUNTANT)) {
            var f = fixture(role); var session = login(f);
            save(session, body(MONTH, null)).andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(0));
            var row = current(f); audit(f, "MONTHLY_RECORD_CREATED", row.getId().toString());
            var update = body(MONTH, row.getVersion()); update.put("revenue", new BigDecimal("125.25"));
            save(session, update).andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(1));
            audit(f, "MONTHLY_RECORD_UPDATED", row.getId().toString()); amount("125.25", current(f).getRevenue());
        }
    }
    @Test void confirmationPersistsExactProvenanceAndHumanAuditTogether() throws Exception {
        var f = fixture(MembershipRole.ACCOUNTANT); var session = login(f); var document = document(f);
        confirm(session, document, request("12.34", "Customer", "revenue", "cash_inflow", false)).andExpect(status().isOk());
        var row = current(f); amount("12.34", row.getRevenue()); amount("12.34", row.getCashInflow());
        amount("77.00", row.getCashBalanceEom()); assertNull(row.getCogs());
        var source = jdbc.queryForMap("SELECT * FROM document_financial_contributions WHERE document_id=?", document);
        assertEquals(f.businessId, source.get("business_id")); assertEquals(f.user.getId(), source.get("actor_user_id"));
        assertEquals(row.getId(), source.get("monthly_record_id")); assertEquals(MONTH, source.get("target_month"));
        amount("12.34", (BigDecimal) source.get("confirmed_amount")); assertEquals(false, source.get("duplicate_override"));
        assertTrue(source.get("source_fingerprint").toString().matches("[0-9a-f]{64}")); assertNotNull(source.get("created_at"));
        assertEquals(DocumentStatus.confirmed, documents.findById(document).orElseThrow().getProcessingStatus());
        audit(f, "DOCUMENT_CONFIRMED", document.toString()); assertEquals(1, count(f, "score_results"));
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void simultaneousDifferentDocumentsNeverLoseUpdatesForAbsentOrExistingMonth(boolean existing) throws Exception {
        var f = fixture(); if (existing) seed(f, MONTH);
        var first = document(f); var second = document(f);
        var results = concurrently(() -> apply(f, first, request("12.34", "First", "revenue", "cash_inflow", false)),
                () -> apply(f, second, request("20.06", "Second", "revenue", "cash_inflow", false)));
        assertTrue(results.stream().allMatch(UploadedDocument.class::isInstance), results.toString());
        assertEquals(1, count(f, "monthly_records")); assertEquals(2, count(f, "document_financial_contributions"));
        var row = current(f); amount(existing ? "132.40" : "32.40", row.getRevenue());
        amount(existing ? "132.40" : "32.40", row.getCashInflow()); amount("77.00", row.getCashBalanceEom()); assertNull(row.getCogs());
        if (existing) amount("12.00", row.getReceivablesOutstanding()); else assertNull(row.getReceivablesOutstanding());
        audit(f, "DOCUMENT_CONFIRMED", first.toString()); audit(f, "DOCUMENT_CONFIRMED", second.toString());
    }
    @Test void simultaneousSameDocumentAppliesExactlyOnceEvenWithOverride() throws Exception {
        var f = fixture(); var doc = document(f); var request = request("50.00", "Same", "cogs", "cash_outflow", true);
        var results = concurrently(() -> apply(f, doc, request), () -> apply(f, doc, request));
        assertEquals(1L, results.stream().filter(UploadedDocument.class::isInstance).count());
        assertEquals(1L, results.stream().filter(DocumentAlreadyConfirmedException.class::isInstance).count());
        amount("50.00", current(f).getCogs()); amount("50.00", current(f).getCashOutflow());
        assertEquals(1, count(f, "document_financial_contributions")); audit(f, "DOCUMENT_CONFIRMED", doc.toString());
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void bothManualAndConfirmationWaitForParentBusinessLockBeforeCreatingAbsentMonth(boolean documentWrite) throws Exception {
        var f = fixture(); var doc = document(f); var started = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            Future<?>[] future = new Future<?>[1];
            new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                businesses.findByIdForUpdate(f.businessId).orElseThrow();
                future[0] = executor.submit(() -> {
                    started.countDown();
                    if (documentWrite) return apply(f, doc, request("2.00", "Locked", "revenue", "none", false));
                    MonthlyRecord record = new MonthlyRecord(); record.setUserId(f.businessId); record.setMonth(MONTH);
                    return monthly.saveMonthlyRecord(record, f.user.getId(), null);
                });
                try { assertTrue(started.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
                assertThrows(TimeoutException.class, () -> future[0].get(250, TimeUnit.MILLISECONDS));
                assertEquals(0, count(f, "monthly_records"));
            });
            assertNotNull(future[0].get(20, TimeUnit.SECONDS)); assertEquals(1, count(f, "monthly_records"));
        }
    }
    @Test void staleLoadedVersionAndAbsentCreateAssertionRejectWithoutLosingConfirmedAmounts() throws Exception {
        var f = fixture(); var session = login(f); var row = seed(f, MONTH); var doc = document(f);
        apply(f, doc, request("20.00", "New", "revenue", "none", false));
        var before = snapshot(f);
        save(session, body(MONTH, row.getVersion())).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("stale_monthly_record"));
        save(session, body(MONTH, null)).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("stale_monthly_record"));
        save(session, body("2026-11", 0L)).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("stale_monthly_record"));
        assertEquals(before, snapshot(f)); amount("120.00", current(f).getRevenue());
    }
    @Test void manualAggregateFloorsCoverEveryConfirmedFlowAndNullCogsButDoNotAddAgain() throws Exception {
        var f = fixture(); var session = login(f);
        apply(f, document(f), request("10.00", "Sales", "revenue", "cash_inflow", false));
        apply(f, document(f), request("20.00", "Overhead", "operating_expenses", "cash_outflow", false));
        apply(f, document(f), request("30.00", "Materials", "cogs", "cash_outflow", false));
        Map<String, BigDecimal> floors = Map.of("revenue", new BigDecimal("10.00"), "operatingExpenses", new BigDecimal("20.00"),
                "cogs", new BigDecimal("30.00"), "cashInflow", new BigDecimal("10.00"), "cashOutflow", new BigDecimal("50.00"));
        var before = snapshot(f);
        for (var floor : floors.entrySet()) {
            var invalid = body(MONTH, current(f).getVersion()); invalid.putAll(floors); invalid.put(floor.getKey(), floor.getValue().subtract(new BigDecimal("0.01")));
            save(session, invalid).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("confirmed_source_conflict"))
                    .andExpect(jsonPath("$.errors." + floor.getKey()).value("confirmed_source_floor"));
        }
        var nullCogs = body(MONTH, current(f).getVersion()); nullCogs.putAll(floors); nullCogs.put("cogs", null);
        save(session, nullCogs).andExpect(status().isConflict()); assertEquals(before, snapshot(f));
        var equal = body(MONTH, current(f).getVersion()); equal.putAll(floors); save(session, equal).andExpect(status().isCreated());
        amount("10.00", current(f).getRevenue()); amount("30.00", current(f).getCogs()); amount("50.00", current(f).getCashOutflow());
        var above = body(MONTH, current(f).getVersion()); above.putAll(floors); above.put("revenue", new BigDecimal("25.00"));
        save(session, above).andExpect(status().isCreated()); amount("25.00", current(f).getRevenue());
        assertEquals(3, count(f, "document_financial_contributions"));
    }
    @Test void manualNullCogsRemainsUnknownWithoutPositiveConfirmedCogsFloor() throws Exception {
        var f = fixture(); var session = login(f); apply(f, document(f), request("10.00", "Sales", "revenue", "none", false));
        save(session, body(MONTH, current(f).getVersion())).andExpect(status().isCreated()); assertNull(current(f).getCogs());
    }
    @Test void normalizedPotentialDuplicateRequiresExplicitOverrideAndRecordsIt() throws Exception {
        var f = fixture(); var session = login(f); var first = document(f); var second = document(f);
        apply(f, first, request("10.0", "  ＡＣＭＥ   Supplies  ", "revenue", "cash_inflow", false));
        var before = snapshot(f);
        confirm(session, second, request("10.00", "acme supplies", "revenue", "cash_inflow", false))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("potential_duplicate_source"));
        assertEquals(before, snapshot(f));
        confirm(session, second, request("10.00", "acme supplies", "revenue", "cash_inflow", true)).andExpect(status().isOk());
        amount("20.00", current(f).getRevenue()); assertEquals(2, count(f, "document_financial_contributions"));
        assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT duplicate_override FROM document_financial_contributions WHERE document_id=?", Boolean.class, second));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(DISTINCT source_fingerprint) FROM document_financial_contributions WHERE business_id=?", Integer.class, f.businessId));
        var after = snapshot(f); confirm(session, second, request("10.00", "acme supplies", "revenue", "cash_inflow", true))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("already_confirmed"));
        assertEquals(after, snapshot(f));
    }
    @Test void economicDuplicateFingerprintDistinguishesDateAmountClassificationAndCashFlow() {
        var f = fixture();
        apply(f, document(f), request("10.00", "Shop", "revenue", "cash_inflow", false));
        apply(f, document(f), request("11.00", "Shop", "revenue", "cash_inflow", false));
        apply(f, document(f), request("10.00", "Shop", "operating_expenses", "cash_inflow", false));
        apply(f, document(f), request("10.00", "Shop", "revenue", "none", false));
        apply(f, document(f), new DocumentConfirmationRequest(MONTH, new BigDecimal("10.00"), LocalDate.of(2026,10,16), "Shop", "revenue", "cash_inflow", null));
        assertEquals(5, count(f, "document_financial_contributions")); amount("41.00", current(f).getRevenue());
    }
    @Test void tenantScopingPreventsCrossBusinessDocumentWritesAndDoesNotShareDuplicateWarnings() throws Exception {
        var first = fixture(); var second = fixture(); var session = login(first); var foreign = document(second);
        var before = snapshot(second);
        confirm(session, foreign, request("5.00", "Vendor", "revenue", "none", false)).andExpect(status().isNotFound());
        assertEquals(before, snapshot(second)); assertEquals(0, count(first, "monthly_records"));
        apply(first, document(first), request("5.00", "Vendor", "revenue", "none", false));
        apply(second, foreign, request("5.00", "Vendor", "revenue", "none", false));
        assertEquals(1, count(first, "document_financial_contributions")); assertEquals(1, count(second, "document_financial_contributions"));
        var spoof = body("2026-11", null); spoof.put("businessId", second.businessId); spoof.put("actorUserId", second.user.getId()); spoof.put("userId", second.businessId);
        before = snapshot(second); save(session, spoof).andExpect(status().isCreated()); assertEquals(before, snapshot(second));
        var own = records.findByUserIdAndMonth(first.businessId, "2026-11").orElseThrow(); audit(first, "MONTHLY_RECORD_CREATED", own.getId().toString());
    }
    @Test void viewerCsrfAndUnauthenticatedRequestsCannotMutateFinancialState() throws Exception {
        var f = fixture(MembershipRole.VIEWER); var session = login(f); var doc = document(f); var before = snapshot(f);
        save(session, body(MONTH, null)).andExpect(status().isForbidden());
        confirm(session, doc, request("5.00", "Viewer", "revenue", "none", false)).andExpect(status().isForbidden());
        mvc.perform(post("/api/records/monthly").cookie(session).contentType("application/json").content(json.writeValueAsString(body(MONTH, null))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/records/monthly").with(csrf(null)).contentType("application/json").content(json.writeValueAsString(body(MONTH, null))))
                .andExpect(status().isUnauthorized());
        assertEquals(before, snapshot(f));
    }
    @Test void everyNumericMonthlyFieldRejectsOverflowFractionAndNegativeBeforeDatabaseMutation() throws Exception {
        var f = fixture(); var session = login(f); var before = snapshot(f);
        for (String field : List.of("cashInflow", "cashOutflow", "revenue", "cogs", "operatingExpenses", "cashBalanceEom", "receivablesOutstanding", "payablesOutstanding", "inventoryValue", "loanOutstanding", "interestExpense")) {
            for (String bad : List.of("1000000000000.00", "1.001", "-0.01")) {
                var body = body(MONTH, null); body.put(field, new BigDecimal(bad));
                save(session, body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors." + field).exists());
            }
        }
        var malformed = body("+10000-10", null); save(session, malformed).andExpect(status().isBadRequest());
        assertEquals(before, snapshot(f));
        var maximum = body(MONTH, null);
        for (String field : List.of("cashInflow", "cashOutflow", "revenue", "cogs", "operatingExpenses", "cashBalanceEom", "receivablesOutstanding", "payablesOutstanding", "inventoryValue", "loanOutstanding", "interestExpense")) maximum.put(field, new BigDecimal("999999999999.99"));
        save(session, maximum).andExpect(status().isCreated()); amount("999999999999.99", current(f).getRevenue()); amount("999999999999.99", current(f).getLoanOutstanding());
    }
    @Test void confirmationNumericInputsAndAggregateAdditionOverflowReturn400AndRollBack() throws Exception {
        var f = fixture(); var session = login(f); var doc = document(f); var before = snapshot(f);
        for (String bad : List.of("1000000000000", "0.001", "-1.00")) {
            confirm(session, doc, request(bad, "Bounds", "revenue", "none", false)).andExpect(status().isBadRequest());
            confirm(session, doc, new DocumentConfirmationRequest(MONTH, BigDecimal.ONE, null, "Balance", "revenue", "none", new BigDecimal(bad)))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(before, snapshot(f));
        confirm(session, doc, new DocumentConfirmationRequest(MONTH, new BigDecimal("999999999999.99"), null, "Max", "revenue", "cash_inflow", new BigDecimal("999999999999.99")))
                .andExpect(status().isOk());
        var overflowing = document(f); before = snapshot(f);
        confirm(session, overflowing, request("0.01", "Overflow", "revenue", "cash_inflow", false)).andExpect(status().isBadRequest());
        assertEquals(before, snapshot(f)); amount("999999999999.99", current(f).getRevenue());
    }
    @Test void mandatoryAuditInsertFailureRollsBackManualAndConfirmationIncludingScoresAndProvenance() throws Exception {
        var f = fixture(); seed(f, MONTH); scoring.calculateAndSaveScore(f.businessId, MONTH); var doc = document(f);
        String function = "pilot_reject_audit_" + UUID.randomUUID().toString().replace("-", "");
        admin("CREATE FUNCTION " + function + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.business_id='" + f.businessId
                + "'::uuid THEN RAISE EXCEPTION 'synthetic mandatory audit failure'; END IF; RETURN NEW; END $$");
        admin("CREATE TRIGGER " + function + " BEFORE INSERT ON security_audit_events FOR EACH ROW EXECUTE FUNCTION " + function + "()");
        try {
            var before = snapshot(f);
            assertThrows(RuntimeException.class, () -> apply(f, doc, request("25.00", "Rollback", "revenue", "cash_inflow", false)));
            assertEquals(before, snapshot(f));
            var incoming = new MonthlyRecord(); incoming.setUserId(f.businessId); incoming.setMonth(MONTH); incoming.setRevenue(new BigDecimal("999.00"));
            assertThrows(RuntimeException.class, () -> monthly.saveMonthlyRecord(incoming, f.user.getId(), current(f).getVersion()));
            assertEquals(before, snapshot(f));
        } finally { admin("DROP TRIGGER " + function + " ON security_audit_events"); admin("DROP FUNCTION " + function + "()"); }
    }
    @Test void scoringFailureRollsBackConfirmationAndManualTransaction() {
        var f = fixture(); seed(f, MONTH); scoring.calculateAndSaveScore(f.businessId, MONTH); var doc = document(f); var before = snapshot(f);
        doThrow(new IllegalStateException("synthetic scoring failure")).when(scoring).calculateAndSaveScore(f.businessId, MONTH);
        try {
            assertThrows(RuntimeException.class, () -> apply(f, doc, request("25.00", "Scoring rollback", "revenue", "cash_inflow", false)));
            assertEquals(before, snapshot(f));
            var incoming = new MonthlyRecord(); incoming.setUserId(f.businessId); incoming.setMonth(MONTH);
            assertThrows(RuntimeException.class, () -> monthly.saveMonthlyRecord(incoming, f.user.getId(), current(f).getVersion()));
            assertEquals(before, snapshot(f));
        } finally { reset(scoring); }
    }
    @Test void confirmedContributionRecalculatesCanonicalScoresAndTheExistingSixRecordWindowOnly() {
        var f = fixture();
        for (int month = 4; month <= 11; month++) { String period = "2026-" + String.format("%02d", month); seed(f, period); scoring.calculateAndSaveScore(f.businessId, period); }
        clearInvocations(scoring);
        apply(f, document(f), new DocumentConfirmationRequest("2026-05", new BigDecimal("45.67"), null, "History", "revenue", "cash_inflow", null));
        for (int month = 5; month <= 10; month++) verify(scoring).calculateAndSaveScore(f.businessId, "2026-" + String.format("%02d", month));
        verify(scoring, never()).calculateAndSaveScore(f.businessId, "2026-04"); verify(scoring, never()).calculateAndSaveScore(f.businessId, "2026-11");
        String calculated = jdbc.queryForObject("SELECT (to_jsonb(s)-'id'-'computed_at')::text FROM score_results s WHERE user_id=? AND month='2026-05'", String.class, f.businessId);
        scoring.calculateAndSaveScore(f.businessId, "2026-05");
        assertEquals(calculated, jdbc.queryForObject("SELECT (to_jsonb(s)-'id'-'computed_at')::text FROM score_results s WHERE user_id=? AND month='2026-05'", String.class, f.businessId));
    }
    @Test void confirmedPartyControlCharactersSerializeSafelyAndNulIsRejectedBeforeMutation() throws Exception {
        var f = fixture(); var session = login(f); var doc = document(f); var before = snapshot(f);
        confirm(session, doc, request("1.00", "bad" + (char) 0 + "party", "revenue", "none", false)).andExpect(status().isBadRequest()); assertEquals(before, snapshot(f));
        String party = "Shop\b\f\n\"\\";
        confirm(session, doc, request("1.00", party, "revenue", "none", false)).andExpect(status().isOk());
        assertEquals(party, json.readTree(documents.findById(doc).orElseThrow().getConfirmedData()).get("confirmed_party").stringValue());
    }
    @Test void contributionPreservesLegacyNegativePointInTimeBalanceWithoutReinterpretingIt() {
        var f = fixture(); var row = seed(f, MONTH);
        row.setCashBalanceEom(new BigDecimal("-25.00")); records.saveAndFlush(row);
        apply(f, document(f), request("5.00", "Legacy balances", "revenue", "cash_inflow", false));
        amount("-25.00", current(f).getCashBalanceEom()); amount("105.00", current(f).getRevenue());
        assertNull(current(f).getCogs()); amount("12.00", current(f).getReceivablesOutstanding());
    }
    List<Object> concurrently(Callable<?> first, Callable<?> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<?> action : List.of(first, second)) futures.add(executor.submit(() -> {
                ready.countDown(); if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("Concurrent start timed out");
                try { return action.call(); } catch (Exception exception) { return exception; }
            }));
            assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
            return List.of(futures.get(0).get(30, TimeUnit.SECONDS), futures.get(1).get(30, TimeUnit.SECONDS));
        }
    }
    void admin(String sql) throws Exception {
        try (var connection = POSTGRES.getPostgresDatabase().getConnection(); var statement = connection.createStatement()) { statement.execute(sql); }
    }
}
