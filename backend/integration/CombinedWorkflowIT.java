package com.app.sme_health_backend.integration;

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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Executed only by scripts/verify-suleman-integration.ps1 in a disposable combined backend.
 * Uses real Suleman calculators, monthly-record rescore hooks, HTTP controllers, Flyway and PostgreSQL.
 * No score fixtures, mocked services, or production database are involved.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CombinedWorkflowIT {
    private static EmbeddedPostgres postgres;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) throws IOException {
        postgres = EmbeddedPostgres.builder().setPort(0).start();
        properties.add("spring.datasource.url", () -> postgres.getJdbcUrl("postgres", "postgres"));
        properties.add("spring.datasource.username", () -> "postgres");
        properties.add("spring.datasource.password", () -> "");
        properties.add("spring.flyway.baseline-on-migrate", () -> "false");
    }

    @AfterAll
    static void stopDatabase() throws IOException {
        if (postgres != null) postgres.close();
    }

    @Test
    void realMonthlyRecordsProduceConsistentDashboardAndBilingualAdviceThroughCorrections() throws Exception {
        UUID id = createProfile();
        saveRecord(id, "2026-07", "100000", "65000", "100000", "40000", "20000", "100000");
        saveRecord(id, "2026-08", "120000", "70000", "120000", "50000", "20000", "150000");
        saveRecord(id, "2026-09", "150000", "85000", "150000", "55000", "25000", "200000");

        JsonNode score = read("/api/scores/" + id + "/latest");
        assertEquals("2026-09", score.get("month").asText());
        assertNotNull(score.get("id"));
        assertEquals(5, score.get("componentScores").size());
        assertTrue(List.of("Strong", "Stable", "Needs Attention", "At Risk").contains(score.get("band").asText()));
        assertEquals(3, jdbc.queryForObject("select count(*) from score_results where user_id=?", Integer.class, id));

        JsonNode insights = read("/api/insights/" + id);
        JsonNode recommendations = read("/api/recommendations/" + id);
        assertEquals(4, insights.size());
        assertEquals(3, recommendations.size());
        assertAdviceMatchesScore(insights, score, "en");
        assertAdviceMatchesScore(recommendations, score, "en");
        assertEquals(version(insights), version(recommendations));
        assertMonthlyComparison(insights, score, read("/api/scores/" + id + "/2026-08"));
        assertEquals(insights, read("/api/insights/" + id), "Unchanged reads preserve IDs and creation timestamps");
        assertEquals(recommendations, read("/api/recommendations/" + id));
        assertDashboardMatches(id, score, insights, recommendations, "en");
        assertSearchMatchesAdvice(id, insights, "insight");
        assertSearchMatchesAdvice(id, recommendations, "recommendation");

        String initialVersion = version(insights);
        List<String> initialIds = ids(insights);
        saveRecord(id, "2026-09", "100000", "95000", "100000", "90000", "8000", "100000");
        JsonNode correctedScore = read("/api/scores/" + id + "/latest");
        assertEquals(score.get("id"), correctedScore.get("id"), "Rescoring updates the existing month");
        assertNotEquals(score.get("computedAt"), correctedScore.get("computedAt"));
        assertNotEquals(score.get("compositeScore"), correctedScore.get("compositeScore"));
        JsonNode correctedInsights = read("/api/insights/" + id);
        JsonNode correctedRecommendations = read("/api/recommendations/" + id);
        assertNotEquals(initialVersion, version(correctedInsights));
        assertNotEquals(initialIds, ids(correctedInsights));
        assertAdviceMatchesScore(correctedInsights, correctedScore, "en");
        assertAdviceMatchesScore(correctedRecommendations, correctedScore, "en");
        assertEquals(version(correctedInsights), version(correctedRecommendations));
        assertDashboardMatches(id, correctedScore, correctedInsights, correctedRecommendations, "en");
        assertSearchMatchesAdvice(id, correctedInsights, "insight");
        assertSearchMatchesAdvice(id, correctedRecommendations, "recommendation");

        // Correct an earlier month through the actual controller. Advice must track the
        // persisted snapshots whether the producer also recalculates later months or not.
        saveRecord(id, "2026-08", "120000", "115000", "120000", "105000", "10000", "150000");
        JsonNode currentAfterPriorEdit = read("/api/scores/" + id + "/latest");
        JsonNode priorCorrectedInsights = read("/api/insights/" + id);
        assertNotEquals(version(correctedInsights), version(priorCorrectedInsights));
        assertMonthlyComparison(priorCorrectedInsights, currentAfterPriorEdit,
                read("/api/scores/" + id + "/2026-08"));

        mvc.perform(patch("/api/profile/{id}/language", id).contentType(APPLICATION_JSON)
                .content("{\"languagePreference\":\"ur\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.languagePreference").value("ur"));
        JsonNode urduInsights = read("/api/insights/" + id);
        JsonNode urduRecommendations = read("/api/recommendations/" + id);
        assertAdviceMatchesScore(urduInsights, currentAfterPriorEdit, "ur");
        assertAdviceMatchesScore(urduRecommendations, currentAfterPriorEdit, "ur");
        assertNotEquals(version(priorCorrectedInsights), version(urduInsights));
        assertEquals(version(urduInsights), version(urduRecommendations));
        for (JsonNode item : urduInsights) assertUrdu(item.get("text").asText());
        for (JsonNode item : urduRecommendations) assertUrdu(item.get("text").asText());
        assertEquals(urduInsights, read("/api/insights/" + id));
        assertDashboardMatches(id, currentAfterPriorEdit, urduInsights, urduRecommendations, "ur");
        assertSearchMatchesAdvice(id, urduInsights, "insight");
        assertSearchMatchesAdvice(id, urduRecommendations, "recommendation");

        JsonNode historicalScore = read("/api/scores/" + id + "/2026-08");
        assertAdviceMatchesScore(read("/api/insights/" + id + "?month=2026-08"), historicalScore, "ur");
        assertAdviceMatchesScore(read("/api/recommendations/" + id + "?month=2026-08"), historicalScore, "ur");
        assertEquals(0, read("/api/insights/" + id + "?month=2026-06").size());

        // Zakat consumes the same persisted monthly record written by Suleman's module.
        mvc.perform(post("/api/zakat/{id}/2026-09/preview", id).contentType(APPLICATION_JSON).content("""
                {"assessment":{"assessmentDate":"2026-09-30","currency":"PKR","nisabMetal":"SILVER",
                 "metalWeightGrams":612.36,"metalPricePerGram":100,"priceSource":"Integration test quotation",
                 "priceTimestamp":"2026-09-30T12:00:00+05:00","haulStatus":"CONFIRMED"}}
                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.zakatDue").value(2500))
                .andExpect(jsonPath("$.source.userId").value(id.toString()))
                .andExpect(jsonPath("$.source.month").value("2026-09"));

        saveRecord(id, "2026-10", "160000", "80000", "160000", "60000", "25000", "220000");
        JsonNode nextScore = read("/api/scores/" + id + "/latest");
        assertEquals("2026-10", nextScore.get("month").asText());
        assertDashboardMatches(id, nextScore, read("/api/insights/" + id),
                read("/api/recommendations/" + id), "ur");
        assertEquals(0, jdbc.queryForObject("""
                select count(*) from (
                    select user_id, month, category from insights where user_id=?
                    group by user_id, month, category having count(*) > 1
                ) duplicate_categories
                """, Integer.class, id));
    }

    @Test
    void dashboardHasNoInventedScoreOrAdviceBeforeTheFirstMonthlyRecord() throws Exception {
        UUID id = createProfile();
        JsonNode dashboard = read("/api/dashboard/" + id);
        assertTrue(dashboard.get("score").isNull());
        assertTrue(dashboard.get("topInsight").isNull());
        assertTrue(dashboard.get("topRecommendation").isNull());
        assertEquals(0, read("/api/insights/" + id).size());
        assertEquals(0, read("/api/recommendations/" + id).size());
    }

    private UUID createProfile() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(post("/api/profile").contentType(APPLICATION_JSON).content("""
                {"userId":"%s","businessType":"retail","languagePreference":"en","whatsappOptIn":false,
                 "paymentBehavior":"immediate","ntnRegistered":true,"businessRegistered":true}
                """.formatted(id)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.paymentBehavior").value("immediate"))
                .andExpect(jsonPath("$.ntnRegistered").value(true))
                .andExpect(jsonPath("$.businessRegistered").value(true));
        return id;
    }

    private void saveRecord(UUID id, String month, String inflow, String outflow, String revenue,
                            String cogs, String expenses, String cash) throws Exception {
        mvc.perform(post("/api/records/monthly").contentType(APPLICATION_JSON).content("""
                {"userId":"%s","month":"%s","cashInflow":%s,"cashOutflow":%s,"revenue":%s,
                 "cogs":%s,"operatingExpenses":%s,"cashBalanceEom":%s,"receivablesOutstanding":0,
                 "payablesOutstanding":0,"inventoryValue":0,"loanOutstanding":0,"interestExpense":0,
                 "financingType":"none"}
                """.formatted(id, month, inflow, outflow, revenue, cogs, expenses, cash)))
                .andExpect(status().isCreated());
        // Suleman's hook currently catches calculation failures; this read makes failures visible.
        mvc.perform(get("/api/scores/{id}/{month}", id, month)).andExpect(status().isOk());
    }

    private JsonNode read(String path) throws Exception {
        return mapper.readTree(mvc.perform(get(path)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private void assertAdviceMatchesScore(JsonNode advice, JsonNode score, String language) {
        assertTrue(advice.size() >= 3);
        for (JsonNode item : advice) {
            assertEquals(score.get("userId"), item.get("userId"));
            assertEquals(score.get("month"), item.get("month"));
            assertEquals(score.get("computedAt"), item.get("sourceComputedAt"));
            assertEquals(language, item.get("language").asText());
            assertEquals(64, item.get("sourceVersion").asText().length());
            assertFalse(item.get("id").isNull());
        }
    }

    private void assertDashboardMatches(UUID id, JsonNode score, JsonNode insights,
                                        JsonNode recommendations, String language) throws Exception {
        JsonNode dashboard = read("/api/dashboard/" + id);
        assertEquals(score, dashboard.get("score"));
        assertEquals(language, dashboard.get("profile").get("languagePreference").asText());
        for (String feature : List.of("topInsight", "topRecommendation")) {
            JsonNode top = dashboard.get(feature);
            assertEquals(score.get("month"), top.get("month"));
            assertEquals(score.get("computedAt"), top.get("sourceComputedAt"));
            assertEquals(version(insights), top.get("sourceVersion").asText());
            assertEquals(language, top.get("language").asText());
        }
        assertEquals(version(insights), version(recommendations));
        assertTrue(ids(insights).contains(dashboard.get("topInsight").get("id").asText()));
        assertTrue(ids(recommendations).contains(dashboard.get("topRecommendation").get("id").asText()));
    }

    private void assertMonthlyComparison(JsonNode insights, JsonNode current, JsonNode previous) {
        JsonNode change = null;
        for (JsonNode item : insights) {
            if ("monthly_change".equals(item.get("category").asText())) change = item;
        }
        assertNotNull(change);
        String text = change.get("text").asText();
        assertTrue(text.contains(previous.get("month").asText()));
        BigDecimal delta = current.get("compositeScore").decimalValue()
                .subtract(previous.get("compositeScore").decimalValue());
        if (delta.signum() == 0) {
            assertTrue(text.contains("unchanged"));
        } else {
            assertTrue(text.contains(delta.signum() > 0 ? "improved" : "declined"));
            assertTrue(text.contains(delta.abs().stripTrailingZeros().toPlainString()));
        }
    }

    private void assertSearchMatchesAdvice(UUID id, JsonNode advice, String type) throws Exception {
        JsonNode results = read("/api/search/" + id + "?q=2026-09&type=" + type);
        assertEquals(advice.size(), results.size(), "Search exposes the refreshed advice rows");
        for (JsonNode item : advice) {
            JsonNode match = null;
            for (JsonNode result : results) {
                if (item.get("id").equals(result.get("id"))) match = result;
            }
            assertNotNull(match, "Search must not return obsolete advice IDs after refresh");
            assertEquals(type, match.get("type").asText());
            assertEquals(item.get("month"), match.get("date"));
            assertEquals(item.get("text"), match.get("description"));
        }
    }

    private static List<String> ids(JsonNode advice) {
        List<String> ids = new ArrayList<>();
        for (JsonNode item : advice) ids.add(item.get("id").asText());
        return ids;
    }

    private static String version(JsonNode advice) {
        return advice.get(0).get("sourceVersion").asText();
    }

    private static void assertUrdu(String text) {
        assertTrue(text.codePoints().anyMatch(c -> c >= 0x600 && c <= 0x6ff));
        assertFalse(text.contains("{"), "Translation placeholders must be fully rendered");
    }
}
