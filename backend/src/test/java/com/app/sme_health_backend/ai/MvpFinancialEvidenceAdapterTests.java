package com.app.sme_health_backend.ai;

import com.app.sme_health_backend.cashflow.dto.*;
import com.app.sme_health_backend.cashflow.service.CashFlowService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.insight.repository.InsightRepository;
import com.app.sme_health_backend.insight.service.InsightService;
import com.app.sme_health_backend.recommendation.entity.*;
import com.app.sme_health_backend.recommendation.repository.RecommendationRepository;
import com.app.sme_health_backend.recommendation.service.RecommendationService;
import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.records.service.MonthlyRecordService;
import com.app.sme_health_backend.scoring.dto.*;
import com.app.sme_health_backend.scoring.entity.ScoreResult;
import com.app.sme_health_backend.scoring.service.ScoringService;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MvpFinancialEvidenceAdapterTests {
    final ScoringService scoring = mock(ScoringService.class);
    final CashFlowService cash = mock(CashFlowService.class);
    final MonthlyRecordService records = mock(MonthlyRecordService.class);
    final InsightRepository insights = mock(InsightRepository.class);
    final RecommendationRepository recommendations = mock(RecommendationRepository.class);
    final InsightService insightService = mock(InsightService.class);
    final RecommendationService recommendationService = mock(RecommendationService.class);
    final MvpFinancialEvidenceAdapter adapter = new MvpFinancialEvidenceAdapter(scoring, cash, records, insights, recommendations, insightService, recommendationService);
    final BusinessAccessContext actor = new BusinessAccessContext(UUID.randomUUID(), UUID.randomUUID(), MembershipRole.VIEWER);
    ScoreResult score;
    @BeforeEach void prepare() {
        score = new ScoreResult(); score.setId(UUID.randomUUID()); score.setUserId(actor.businessId());
        score.setMonth("2026-09"); score.setCompositeScore(new BigDecimal("48.75")); score.setBand("Needs Attention");
        score.setWeakestComponent("cashflow"); score.setDataCompleteness(new BigDecimal("0.80"));
        score.setMethodologyVersion("health-score-v1"); score.setComputedAt(LocalDateTime.parse("2026-10-01T09:00:00"));
        score.setComponentScores(new ComponentScoresDto(new BigDecimal("30.00"), new BigDecimal("60.00"), null, new BigDecimal("45.00"), new BigDecimal("60.00")));
        score.setExplanation(new ScoreExplanation(2, "2026-08", new BigDecimal("55.00"), new BigDecimal("-6.25"),
                Map.of("cashflow", new ScoreExplanation.Component("AVAILABLE", new BigDecimal("30.00"), new BigDecimal("0.30"), new BigDecimal("0.30"), 2,
                        "snapshot", "monthly", List.of(new ScoreExplanation.Driver("cashBufferMonths", "0.2500", "months")), null, null)), List.of()));
        when(scoring.getLatestScore(actor.businessId())).thenReturn(Optional.of(score));
        when(scoring.getScore(actor.businessId(), "2026-09")).thenReturn(Optional.of(score));
        when(scoring.getScore(actor.businessId(), "2026-08")).thenReturn(Optional.empty());
    }
    AiTypes.Context build(String type) { return adapter.build(actor, new AiTypes.Request("ask", "en", "question", null, null, null), type); }
    String value(AiTypes.Context c, String id) { return c.evidence().stream().filter(f -> f.id().equals(id)).findFirst().orElseThrow().value(); }
    @Test void usesExistingExactScoreAndEvidenceWithoutRecalculating() {
        var c = build("score");
        assertEquals("48.75", value(c, "score.total")); assertEquals("-6.25", value(c, "score.delta"));
        assertEquals("0.2500", value(c, "driver.cashflow.cashBufferMonths")); assertNull(value(c, "score.repayment"));
        assertTrue(c.limitationCodes().contains("missing_data"));
        verify(scoring, never()).calculateAndSaveScore(any(), any());
        verify(insightService, never()).getInsights(any()); verify(recommendationService, never()).getRecommendations(any());
        verifyNoInteractions(records, cash, recommendations);
    }
    @Test void cashHistoryAndBackendProjectionRetainExactAmountsAndSeparateModes() {
        when(cash.getCashFlowHistory(actor.businessId())).thenReturn(List.of(new CashFlowChartPointResponse("2026-09", new BigDecimal("1.0000"), new BigDecimal("2.0000"), new BigDecimal("-1.0000"), BigDecimal.ZERO)));
        when(cash.getTrendProjection(actor.businessId())).thenReturn(new CashFlowProjectionResponse("2026-10", new BigDecimal("-100.2500"), "DOWN", "LOW", 3, null));
        var c = build("cashflow"); assertEquals("-1.0000", value(c, "cash.2026-09.net")); assertEquals("0", value(c, "cash.2026-09.balance"));
        var projection = build("projection"); assertEquals("-100.2500", value(projection, "projection"));
        assertTrue(projection.evidence().stream().allMatch(f -> "PLAN".equals(f.sourceMode())));
        assertTrue(projection.limitationCodes().contains("projection"));
    }
    @Test void explicitOldMonthNeverUsesCurrentProjection() {
        when(cash.getCashFlowHistory(actor.businessId())).thenReturn(List.of(new CashFlowChartPointResponse("2026-09", null, null, null, null)));
        when(cash.getTrendProjection(actor.businessId())).thenReturn(new CashFlowProjectionResponse("2026-10", new BigDecimal("45.50"), "UP", "LOW", 3, null));
        var c = adapter.build(actor, new AiTypes.Request("ask", "en", "projection", null, "2026-08", null), "projection");
        assertEquals("projection_period_unavailable", c.unavailableReason()); assertTrue(c.evidence().isEmpty());
    }
    @Test void explicitProjectionTargetMonthUsesTheActualBackendTarget() {
        when(cash.getCashFlowHistory(actor.businessId())).thenReturn(List.of(new CashFlowChartPointResponse("2026-09", null, null, null, null)));
        when(cash.getTrendProjection(actor.businessId())).thenReturn(new CashFlowProjectionResponse("2026-10", new BigDecimal("45.50"), "UP", "LOW", 3, null));
        var c = adapter.build(actor, new AiTypes.Request("ask", "en", "projection 2026-10", null, "2026-10", null), "projection");
        assertNull(c.unavailableReason()); assertEquals("45.50", value(c, "projection"));
        assertTrue(c.evidence().stream().allMatch(e -> "2026-10".equals(e.period()) && "PLAN".equals(e.sourceMode())));
    }
    @Test void recordsRemainScopedAndNullIsNotZero() {
        MonthlyRecord record = new MonthlyRecord(); record.setUserId(actor.businessId()); record.setMonth("2026-09");
        record.setRevenue(new BigDecimal("999999999999.1250")); record.setOperatingExpenses(BigDecimal.ZERO); record.setCogs(null);
        when(records.getMonthlyRecord(actor.businessId(), "2026-09")).thenReturn(Optional.of(record));
        var c = adapter.build(actor, new AiTypes.Request("ask", "ur", "ماہانہ ریکارڈ", null, "2026-09", null), "records");
        assertEquals("999999999999.1250", value(c, "record.2026-09.revenue")); assertEquals("0", value(c, "record.2026-09.expenses"));
        assertNull(value(c, "record.2026-09.cogs")); verify(records, never()).getUserRecords(any());
    }
    @Test void absentScoreIsUnavailableAndOtherBusinessSourceIsNotFound() {
        when(scoring.getLatestScore(actor.businessId())).thenReturn(Optional.empty());
        assertEquals("insufficient_evidence", build("score").unavailableReason());
        assertThrows(ResourceNotFoundException.class, () -> adapter.build(actor,
                new AiTypes.Request("recommendation", "en", null, UUID.randomUUID(), null, null), "recommendation"));
    }
    @Test void recommendationLifecycleIsReadOnlyAndInvalidatesFingerprint() {
        Recommendation rec = new Recommendation(); ReflectionTestUtils.setField(rec, "id", UUID.randomUUID());
        rec.setUserId(actor.businessId()); rec.setMonth("2026-09"); rec.setText("Review recorded expenses.");
        rec.setCategory("cashflow"); rec.setPriority("high"); rec.setStatus(RecommendationStatus.DONE);
        rec.setSourceComputedAt(score.getComputedAt()); rec.setSourceVersion("fixture-source-version");
        when(recommendations.findByUserIdAndMonthOrderByCreatedAtDesc(actor.businessId(), "2026-09")).thenReturn(List.of(rec));
        when(recommendationService.generateRecommendations(score, "en")).thenReturn(List.of(rec));
        var request = new AiTypes.Request("recommendation", "en", null, rec.getId(), "2026-09", null);
        var c = adapter.build(actor, request, "recommendation");
        assertEquals("DONE", value(c, "recommendation.cashflow.status")); assertEquals("high", value(c, "recommendation.cashflow.priority"));
        assertEquals(RecommendationStatus.DONE, rec.getStatus()); verify(recommendations, never()).save(any());
        verify(recommendations, never()).saveAll(any()); verify(recommendationService, never()).updateStatus(any(), any(), any(), any());
        rec.setStatus(RecommendationStatus.DISMISSED);
        assertNotEquals(c.fingerprint(), adapter.build(actor, request, "recommendation").fingerprint());
        rec.setSourceComputedAt(score.getComputedAt().minusDays(1));
        assertEquals("stale_source", adapter.build(actor, request, "recommendation").unavailableReason());
    }
    @Test void unexpectedWrongTenantServiceResultFailsClosed() {
        score.setUserId(UUID.randomUUID()); assertThrows(IllegalStateException.class, () -> build("score"));
    }
    @Test void explicitCashMonthOutsideLatestChartReusesExistingRecordMapping() {
        MonthlyRecord record = new MonthlyRecord(); record.setUserId(actor.businessId()); record.setMonth("2025-01");
        record.setCashInflow(new BigDecimal("500.0000")); record.setCashOutflow(new BigDecimal("700.0000")); record.setCashBalanceEom(new BigDecimal("50.0000"));
        when(records.getMonthlyRecord(actor.businessId(), "2025-01")).thenReturn(Optional.of(record));
        var c = adapter.build(actor, new AiTypes.Request("ask", "en", "cash 2025-01", null, "2025-01", null), "cashflow");
        assertEquals("-200.0000", value(c, "cash.2025-01.net"));
        assertEquals("50.0000", value(c, "cash.2025-01.balance"));
    }
}
