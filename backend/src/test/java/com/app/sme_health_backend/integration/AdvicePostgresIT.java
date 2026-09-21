package com.app.sme_health_backend.integration;

import com.app.sme_health_backend.insight.entity.Insight;
import com.app.sme_health_backend.insight.service.InsightService;
import com.app.sme_health_backend.recommendation.service.RecommendationService;
import com.app.sme_health_backend.shared.advice.AdviceContextService;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real PostgreSQL, Flyway, HTTP, services and repositories; no mocked database or scores. */
@SpringBootTest
@AutoConfigureMockMvc
class AdvicePostgresIT {
    private static EmbeddedPostgres postgres;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired InsightService insights;
    @Autowired RecommendationService recommendations;
    @Autowired AdviceContextService adviceContext;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired DataSource dataSource;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) throws IOException {
        postgres = EmbeddedPostgres.builder().setPort(0).start();
        properties.add("spring.datasource.url", () -> postgres.getJdbcUrl("postgres", "postgres"));
        properties.add("spring.datasource.username", () -> "postgres");
        properties.add("spring.datasource.password", () -> "");
        properties.add("spring.flyway.baseline-on-migrate", () -> "false");
    }

    @AfterAll static void stopDatabase() throws IOException { if (postgres != null) postgres.close(); }

    @Test
    void httpAdviceTracksPersistedScoreCorrectionsMonthAndLanguage() throws Exception {
        UUID id = createProfile();
        mvc.perform(get("/api/insights/{id}", id)).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/recommendations/{id}", id)).andExpect(status().isOk()).andExpect(content().json("[]"));
        score(id, "2026-08", "50.00", "Needs Attention", LocalDateTime.of(2026, 8, 31, 12, 0));
        score(id, "2026-09", "72.00", "Stable", LocalDateTime.of(2026, 9, 30, 12, 0));

        JsonNode first = advice("insights", id, "");
        assertEquals(4, first.size());
        assertTrue(first.toString().contains("22"));
        assertEquals("2026-09", first.get(0).get("month").asText());
        assertEquals("en", first.get(0).get("language").asText());
        String firstVersion = first.get(0).get("sourceVersion").asText();
        assertEquals(64, firstVersion.length());
        assertEquals(first, advice("insights", id, ""));
        JsonNode initialRecommendations = advice("recommendations", id, "");
        assertEquals(3, initialRecommendations.size());
        assertEquals(firstVersion, initialRecommendations.get(0).get("sourceVersion").asText());
        assertEquals(initialRecommendations, advice("recommendations", id, ""));

        // Earlier-month corrections invalidate the comparison even if the current score is unchanged.
        score(id, "2026-08", "60.00", "Stable", LocalDateTime.of(2026, 10, 1, 12, 0));
        JsonNode afterPreviousEdit = advice("insights", id, "");
        assertNotEquals(firstVersion, afterPreviousEdit.get(0).get("sourceVersion").asText());
        assertTrue(afterPreviousEdit.toString().contains("12"));

        // Content changes are detected even if a producer reused a computation timestamp.
        score(id, "2026-09", "35.00", "At Risk", LocalDateTime.of(2026, 9, 30, 12, 0));
        JsonNode afterEdit = advice("recommendations", id, "");
        assertTrue(afterEdit.toString().contains("At Risk"));
        assertNotEquals(firstVersion, afterEdit.get(0).get("sourceVersion").asText());

        mvc.perform(patch("/api/profile/{id}/language", id).contentType(APPLICATION_JSON)
                .content("{\"languagePreference\":\"ur\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.languagePreference").value("ur"));
        for (String feature : List.of("insights", "recommendations")) {
            JsonNode urdu = advice(feature, id, "");
            assertEquals("ur", urdu.get(0).get("language").asText());
            assertTrue(urdu.get(0).get("text").asText().codePoints().anyMatch(c -> c >= 0x600 && c <= 0x6ff));
            JsonNode historical = advice(feature, id, "?month=2026-08");
            assertEquals("ur", historical.get(0).get("language").asText());
            assertEquals("2026-08", historical.get(0).get("month").asText());
            assertEquals(0, advice(feature, id, "?month=2026-07").size());
        }
        mvc.perform(patch("/api/profile/{id}/language", id).contentType(APPLICATION_JSON)
                .content("{\"languagePreference\":\"en\"}")).andExpect(status().isOk());
        assertEquals("en", advice("insights", id, "").get(0).get("language").asText());
        assertEquals("en", advice("recommendations", id, "").get(0).get("language").asText());
    }

    @Test
    void repeatedConcurrentGenerationHasOneStableCategorySet() throws Exception {
        UUID id = createProfile();
        score(id, "2026-09", "72.00", "Stable", LocalDateTime.of(2026, 9, 30, 12, 0));
        try (var pool = Executors.newFixedThreadPool(8)) {
            List<Callable<List<UUID>>> calls = new ArrayList<>();
            for (int n = 0; n < 16; n++) {
                calls.add(() -> {
                    List<Insight> result = insights.getInsights(id);
                    recommendations.getRecommendations(id);
                    return result.stream().map(Insight::getId).toList();
                });
            }
            var futures = pool.invokeAll(calls, 30, TimeUnit.SECONDS);
            List<UUID> expected = futures.get(0).get();
            for (var future : futures) assertEquals(expected, future.get());
        }
        assertEquals(3, jdbc.queryForObject("select count(*) from insights where user_id=?", Integer.class, id));
        assertEquals(3, jdbc.queryForObject("select count(*) from recommendations where user_id=?", Integer.class, id));
    }

    @Test
    void missingPreviousScoreCannotAppearBetweenAdviceReadsInOneTransaction() throws Exception {
        UUID id = createProfile();
        score(id, "2026-09", "72.00", "Stable", LocalDateTime.of(2026, 9, 30, 12, 0));
        CountDownLatch writerStarted = new CountDownLatch(1);
        AtomicInteger writerPid = new AtomicInteger();
        try (var pool = Executors.newSingleThreadExecutor()) {
            var pendingWrite = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<?>>();
            String originalVersion = new TransactionTemplate(transactionManager).execute(status -> {
                var snapshot = adviceContext.latest(id).orElseThrow();
                assertNull(snapshot.previousScore());
                var before = insights.getInsights(id);
                assertEquals(3, before.size());
                pendingWrite.set(pool.submit(() -> {
                    try (var connection = dataSource.getConnection();
                         var pidQuery = connection.createStatement();
                         var pidResult = pidQuery.executeQuery("select pg_backend_pid()")) {
                        assertTrue(pidResult.next());
                        writerPid.set(pidResult.getInt(1));
                        writerStarted.countDown();
                        try (var insert = connection.prepareStatement("""
                            insert into score_results(user_id,month,composite_score,band,component_scores,
                                weakest_component,data_completeness,computed_at)
                            select user_id,'2026-08',composite_score,band,component_scores,
                                weakest_component,data_completeness,computed_at from score_results
                            where user_id=? and month='2026-09'
                            """)) {
                            insert.setObject(1, id);
                            insert.executeUpdate();
                        }
                        return null;
                    }
                }));
                try {
                    assertTrue(writerStarted.await(5, TimeUnit.SECONDS));
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                    boolean blocked = false;
                    while (System.nanoTime() < deadline && !pendingWrite.get().isDone()) {
                        jdbc.execute("select pg_stat_clear_snapshot()");
                        blocked = Boolean.TRUE.equals(jdbc.queryForObject(
                                "select wait_event_type = 'Lock' from pg_stat_activity where pid=?",
                                Boolean.class, writerPid.get()));
                        if (blocked) break;
                        Thread.sleep(10);
                    }
                    assertTrue(blocked, "A new prior score must wait for the profile snapshot lock");
                    assertFalse(pendingWrite.get().isDone());
                    var sameSnapshot = adviceContext.latest(id).orElseThrow();
                    assertNull(sameSnapshot.previousScore());
                    assertEquals(snapshot.sourceVersion(), sameSnapshot.sourceVersion());
                    assertEquals(snapshot.sourceVersion(), recommendations.getRecommendations(id).get(0).getSourceVersion());
                    assertEquals(snapshot.sourceVersion(), before.get(0).getSourceVersion());
                    return snapshot.sourceVersion();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            });
            pendingWrite.get().get(5, TimeUnit.SECONDS);
            var afterCommit = insights.getInsights(id);
            assertEquals(4, afterCommit.size());
            assertNotEquals(originalVersion, afterCommit.get(0).getSourceVersion());
        }
    }

    @Test
    void gapAndLegacyAdviceNeverProduceInventedScoreOrWrongMonth() throws Exception {
        UUID id = createProfile();
        jdbc.update("insert into insights(user_id,month,insight_text,category,priority) values (?,'2026-09','Legacy mock','liquidity','high')", id);
        assertTrue(insights.getInsights(id).isEmpty());
        score(id, "2026-07", "50.00", "Needs Attention", LocalDateTime.of(2026, 7, 31, 12, 0));
        score(id, "2026-09", "72.00", "Stable", LocalDateTime.of(2026, 9, 30, 12, 0));
        assertEquals(3, insights.getInsights(id).size());
        assertEquals(0, jdbc.queryForObject("select count(*) from insights where user_id=? and category='liquidity'", Integer.class, id));
        assertTrue(insights.getInsights(id, "2026-08").isEmpty());
        mvc.perform(get("/api/insights/{id}", id).param("month", "2026-99")).andExpect(status().isBadRequest());
    }

    @Test
    void zakatPreviewStillWorksAgainstSavedMonthlyRecord() throws Exception {
        UUID id = createProfile();
        jdbc.update("insert into monthly_records(user_id,month,cash_balance_eom,receivables_outstanding,payables_outstanding,inventory_value,loan_outstanding,interest_expense) values (?,'2026-09',100000,0,0,0,0,0)", id);
        mvc.perform(post("/api/zakat/{id}/2026-09/preview", id).contentType(APPLICATION_JSON).content("""
            {"assessment":{"assessmentDate":"2026-09-30","currency":"PKR","nisabMetal":"SILVER",
             "metalWeightGrams":612.36,"metalPricePerGram":100,"priceSource":"Test quotation",
             "priceTimestamp":"2026-09-30T12:00:00+05:00","haulStatus":"CONFIRMED"}}
            """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.zakatDue").value(2500));
    }

    private UUID createProfile() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(post("/api/profile").contentType(APPLICATION_JSON).content("""
            {"userId":"%s","businessType":"retail","languagePreference":"en","whatsappOptIn":false}
            """.formatted(id))).andExpect(status().isCreated());
        return id;
    }

    private JsonNode advice(String feature, UUID id, String query) throws Exception {
        String body = mvc.perform(get("/api/" + feature + "/" + id + query))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return mapper.readTree(body);
    }

    private void score(UUID id, String month, String value, String band, LocalDateTime time) {
        jdbc.update("""
            insert into score_results(user_id,month,composite_score,band,component_scores,weakest_component,data_completeness,computed_at)
            values (?,?,?, ?,cast(? as jsonb),'cashflow',0.80,?)
            on conflict(user_id,month) do update set composite_score=excluded.composite_score,band=excluded.band,
                component_scores=excluded.component_scores,computed_at=excluded.computed_at
            """, id, month, new BigDecimal(value), band,
                "{\"cashflow\":40,\"profitability\":70,\"repayment\":null,\"trend\":70,\"compliance\":80}", time);
    }
}
