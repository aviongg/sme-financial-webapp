package com.app.sme_health_backend.ai;

import com.app.sme_health_backend.cashflow.service.CashFlowService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.insight.repository.InsightRepository;
import com.app.sme_health_backend.insight.service.InsightService;
import com.app.sme_health_backend.recommendation.repository.RecommendationRepository;
import com.app.sme_health_backend.recommendation.service.RecommendationService;
import com.app.sme_health_backend.records.service.MonthlyRecordService;
import com.app.sme_health_backend.scoring.entity.ScoreResult;
import com.app.sme_health_backend.scoring.service.ScoringService;
import com.app.sme_health_backend.shared.advice.AdviceContextService;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;

/**
 * B-owned bounded read adapter, not C11 or a calculation authority. Reuses persisted scoring,
 * cashflow and monthly services plus pure existing advice rendering. Dashboard/AdviceContext
 * orchestration is intentionally not invoked: it refreshes advice and acquires write locks.
 * The repeatable-read snapshot ends before any provider/network operation.
 */
@Service
public class MvpFinancialEvidenceAdapter {
    private final ScoringService scoring;
    private final CashFlowService cashflow;
    private final MonthlyRecordService records;
    private final InsightRepository insights;
    private final RecommendationRepository recommendations;
    private final InsightService insightService;
    private final RecommendationService recommendationService;

    public MvpFinancialEvidenceAdapter(ScoringService scoring, CashFlowService cashflow,
            MonthlyRecordService records, InsightRepository insights, RecommendationRepository recommendations,
            InsightService insightService, RecommendationService recommendationService) {
        this.scoring = scoring; this.cashflow = cashflow; this.records = records;
        this.insights = insights; this.recommendations = recommendations;
        this.insightService = insightService; this.recommendationService = recommendationService;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AiTypes.Context build(BusinessAccessContext actor, AiTypes.Request request, String topic) {
        UUID businessId = actor.businessId();
        Builder b = new Builder(topic, request.language());
        if ("unsupported".equals(topic)) return b.finish("unsupported_question");
        if (request.month() != null) AdviceContextService.validateMonth(request.month());
        ScoreResult score = (request.month() == null ? scoring.getLatestScore(businessId)
                : scoring.getScore(businessId, request.month())).orElse(null);
        if (score != null && !businessId.equals(score.getUserId())) throw new IllegalStateException("Evidence scope mismatch");
        if (Set.of("dashboard", "score", "insight", "recommendation", "cashflow").contains(topic) && score != null) {
            addScore(b, score);
        }
        if (Set.of("insight", "recommendation").contains(topic)) {
            if (score == null && request.sourceId() != null) throw new ResourceNotFoundException("Financial source not found");
            if (score != null) {
                String error = addAdvice(b, businessId, request, topic, score);
                if (error != null) return b.finish(error);
            }
        }
        if (Set.of("cashflow", "records", "projection", "dashboard").contains(topic)) {
            var history = cashflow.getCashFlowHistory(businessId);
            if (history == null) history = List.of();
            if ("projection".equals(topic)) {
                var projection = cashflow.getTrendProjection(businessId);
                // A question's month names the projected period, not the latest historical input.
                // Only the actual target returned by the existing latest-history projection is supported.
                if (request.month() != null && (projection == null || !request.month().equals(projection.getProjectedMonth())))
                    return b.finish("projection_period_unavailable");
                if (projection != null && projection.getProjectedNetCashFlow() != null) {
                    b.fact("projection", "projection", "projection", decimal(projection.getProjectedNetCashFlow()),
                            "recorded_amount", projection.getProjectedMonth(), "PLAN");
                    b.fact("projection.direction", "projection", "direction", projection.getTrendDirection(), "category", projection.getProjectedMonth(), "PLAN");
                    b.fact("projection.confidence", "projection", "confidence", projection.getConfidence(), "category", projection.getProjectedMonth(), "PLAN");
                }
                b.limitations.add("projection");
            } else if ("records".equals(topic)) {
                List<String> months = request.month() != null ? List.of(request.month())
                        : history.stream().map(p -> p.getMonth()).limit(6).toList();
                for (String month : months) records.getMonthlyRecord(businessId, month).ifPresent(record -> {
                    if (!businessId.equals(record.getUserId())) throw new IllegalStateException("Evidence scope mismatch");
                    b.revisions.add("record:" + record.getId() + ":" + record.getUpdatedAt());
                    b.fact("record." + month + ".revenue", "monthly_record", "revenue", decimal(record.getRevenue()), "recorded_amount", month, "SNAPSHOT");
                    b.fact("record." + month + ".expenses", "monthly_record", "expenses", decimal(record.getOperatingExpenses()), "recorded_amount", month, "SNAPSHOT");
                    b.fact("record." + month + ".cogs", "monthly_record", "cogs", decimal(record.getCogs()), "recorded_amount", month, "SNAPSHOT");
                    b.fact("record." + month + ".balance", "monthly_record", "balance", decimal(record.getCashBalanceEom()), "recorded_amount", month, "SNAPSHOT");
                });
            } else {
                if (request.month() != null) {
                    // CashFlowService exposes a six-month chart. For an explicitly selected older
                    // month reuse its existing DTO mapping from that authorized monthly record.
                    history = records.getMonthlyRecord(businessId, request.month()).map(record -> {
                        if (!businessId.equals(record.getUserId())) throw new IllegalStateException("Evidence scope mismatch");
                        b.revisions.add("record:" + record.getId() + ":" + record.getUpdatedAt());
                        return List.of(com.app.sme_health_backend.cashflow.dto.CashFlowChartPointResponse.fromEntity(record));
                    }).orElseGet(List::of);
                }
                for (var point : history.stream().filter(p -> request.month() == null || request.month().equals(p.getMonth())).limit(6).toList()) {
                    String id = "cash." + point.getMonth() + ".";
                    b.fact(id + "inflow", "cashflow", "inflow", decimal(point.getInflow()), "recorded_amount", point.getMonth(), "SNAPSHOT");
                    b.fact(id + "outflow", "cashflow", "outflow", decimal(point.getOutflow()), "recorded_amount", point.getMonth(), "SNAPSHOT");
                    b.fact(id + "net", "cashflow", "net", decimal(point.getNet()), "recorded_amount", point.getMonth(), "SNAPSHOT");
                    b.fact(id + "balance", "cashflow", "balance", decimal(point.getRunningBalance()), "recorded_amount", point.getMonth(), "SNAPSHOT");
                }
            }
        }
        return b.finish(b.evidence.isEmpty() ? "insufficient_evidence" : null);
    }

    private void addScore(Builder b, ScoreResult score) {
        String month = score.getMonth();
        b.revisions.add("score:" + score.getId() + ":" + score.getComputedAt() + ":" + score.getMethodologyVersion());
        b.fact("score.total", "score", "score", decimal(score.getCompositeScore()), "score_points", month, "SNAPSHOT");
        b.fact("score.band", "score", "band", score.getBand(), "category", month, "SNAPSHOT");
        b.fact("score.weakest", "score", "weakest", score.getWeakestComponent(), "category", month, "SNAPSHOT");
        b.fact("score.completeness", "score", "completeness", decimal(score.getDataCompleteness()), "fraction", month, "SNAPSHOT");
        if (score.getComponentScores() != null) score.getComponentScores().toMap().forEach((key, value) ->
                b.fact("score." + key, "component", key, decimal(value), "score_points", month, "SNAPSHOT"));
        var explanation = score.getExplanation();
        if (explanation == null) { b.limitations.add("missing_data"); return; }
        if (explanation.historyMonthsAvailable() < 3) b.limitations.add("missing_data");
        b.fact("score.delta", "score", "delta", decimal(explanation.overallDelta()), "score_points", month, "SNAPSHOT");
        if (explanation.components() != null) for (var entry : new TreeMap<>(explanation.components()).entrySet()) {
            var component = entry.getValue();
            if (component == null || "UNAVAILABLE".equals(component.status())) { b.limitations.add("missing_data"); continue; }
            if (component.drivers() != null) for (var driver : component.drivers()) {
                // Only documented existing evidence drivers, no new AI calculation or threshold.
                if (Set.of("cashBufferMonths", "netMargin", "dso").contains(driver.key()))
                    b.fact("driver." + entry.getKey() + "." + driver.key(), "score_driver", driver.key(),
                            driver.value(), driver.unit() == null ? "recorded_value" : driver.unit(), month, "SNAPSHOT");
            }
        }
        // Existing EvidenceAdvice is consumed through the pure methods of current insight/recommendation services.
        ScoreResult previous = scoring.getScore(score.getUserId(), YearMonth.parse(month).minusMonths(1).toString()).orElse(null);
        if (previous != null) {
            if (!score.getUserId().equals(previous.getUserId())) throw new IllegalStateException("Evidence scope mismatch");
            b.revisions.add("previous-score:" + previous.getId() + ":" + previous.getComputedAt());
            b.fact("score.previous", "score", "score", decimal(previous.getCompositeScore()), "score_points", previous.getMonth(), "SNAPSHOT");
            String previousBuffer = com.app.sme_health_backend.shared.advice.EvidenceAdvice.driver(previous, "cashflow", "cashBufferMonths");
            if (previousBuffer != null) b.fact("driver.previous.cashBufferMonths", "score_driver", "cashBufferMonths", previousBuffer, "months", previous.getMonth(), "SNAPSHOT");
        }
        var rendered = insightService.generateInsights(score, b.language, previous);
        if (rendered != null) rendered.stream().filter(i -> Objects.equals(i.getCategory(), score.getWeakestComponent())
                || "monthly_change".equals(i.getCategory()) || "data_quality".equals(i.getCategory())).limit(3).forEach(i ->
                    b.clause("score-advice-" + i.getCategory(), i.getText(), b.evidence.stream().map(AiTypes.Evidence::id).toList()));
    }

    private String addAdvice(Builder b, UUID businessId, AiTypes.Request request, String topic, ScoreResult score) {
        if ("insight".equals(topic)) {
            var stored = insights.findByUserIdAndMonthOrderByCreatedAtDesc(businessId, score.getMonth());
            var selected = stored.stream().filter(i -> request.sourceId() == null || request.sourceId().equals(i.getId())).limit(6).toList();
            if (request.sourceId() != null && selected.isEmpty()) throw new ResourceNotFoundException("Financial source not found");
            if (selected.isEmpty()) return "insufficient_evidence";
            var previous = scoring.getScore(businessId, YearMonth.parse(score.getMonth()).minusMonths(1).toString()).orElse(null);
            var translated = insightService.generateInsights(score, b.language, previous);
            for (var item : selected) {
                if (!businessId.equals(item.getUserId())) throw new IllegalStateException("Evidence scope mismatch");
                if (!Objects.equals(item.getSourceComputedAt(), score.getComputedAt())) { b.limitations.add("stale_source"); return "stale_source"; }
                String text = translated.stream().filter(i -> i.getCategory().equals(item.getCategory())).map(i -> i.getText()).findFirst().orElse(item.getText());
                b.revisions.add("insight:" + item.getId() + ":" + item.getSourceVersion());
                b.fact("insight." + item.getCategory(), "insight", "insight", text, "text", item.getMonth(), "SNAPSHOT");
                b.fact("insight." + item.getCategory() + ".priority", "insight", "priority", item.getPriority(), "category", item.getMonth(), "SNAPSHOT");
            }
        } else {
            var stored = recommendations.findByUserIdAndMonthOrderByCreatedAtDesc(businessId, score.getMonth());
            var selected = stored.stream().filter(i -> request.sourceId() == null || request.sourceId().equals(i.getId())).limit(6).toList();
            if (request.sourceId() != null && selected.isEmpty()) throw new ResourceNotFoundException("Financial source not found");
            if (selected.isEmpty()) return "insufficient_evidence";
            var translated = recommendationService.generateRecommendations(score, b.language);
            for (var item : selected) {
                if (!businessId.equals(item.getUserId())) throw new IllegalStateException("Evidence scope mismatch");
                if (!Objects.equals(item.getSourceComputedAt(), score.getComputedAt())) { b.limitations.add("stale_source"); return "stale_source"; }
                String text = translated.stream().filter(i -> i.getCategory().equals(item.getCategory())).map(i -> i.getText()).findFirst().orElse(item.getText());
                String id = "recommendation." + item.getCategory();
                b.revisions.add("recommendation:" + item.getId() + ":" + item.getSourceVersion() + ":" + item.getStatus() + ":" + item.getStatusUpdatedAt());
                b.fact(id, "recommendation", "recommendation", text, "text", item.getMonth(), "SNAPSHOT");
                b.fact(id + ".priority", "recommendation", "priority", item.getPriority(), "category", item.getMonth(), "SNAPSHOT");
                b.fact(id + ".status", "recommendation", "status", item.getStatus() == null ? null : item.getStatus().name(), "category", item.getMonth(), "SNAPSHOT");
                b.considerations.add(new AiTypes.Clause("action-" + item.getCategory(), text, List.of(id, id + ".priority", id + ".status")));
            }
        }
        return null;
    }

    static String decimal(BigDecimal value) { return value == null ? null : value.toPlainString(); }
    private static final class Builder {
        final String topic, language;
        final List<AiTypes.Evidence> evidence = new ArrayList<>();
        final List<AiTypes.Clause> interpretations = new ArrayList<>(), considerations = new ArrayList<>();
        final Set<String> limitations = new LinkedHashSet<>(List.of("snapshot", "no_causality", "unsupported_modules"));
        final List<String> revisions = new ArrayList<>();
        Builder(String topic, String language) { this.topic = topic; this.language = language; }
        void fact(String id, String kind, String labelKey, String value, String unit, String period, String mode) {
            if (evidence.size() >= 64) { limitations.add("missing_data"); return; }
            String label = AiText.label(labelKey, language);
            evidence.add(new AiTypes.Evidence(id, kind, label, value, unit, period, mode));
            if (value == null) limitations.add("missing_data");
            if ("recorded_amount".equals(unit)) limitations.add("currency_unspecified");
            String display = value == null ? AiText.choose(language, "not recorded", "درج نہیں") : value;
            clause("fact-" + id, label + (period == null ? "" : " (" + period + ")") + ": " + display, List.of(id));
        }
        void clause(String id, String text, List<String> ids) {
            if (text != null && text.length() <= 2000) interpretations.add(new AiTypes.Clause(id, text, ids));
        }
        AiTypes.Context finish(String reason) {
            if (evidence.isEmpty()) limitations.add("missing_data");
            var limits = List.copyOf(limitations);
            String fingerprint = AiJson.hash(List.of(topic, language, evidence, interpretations, considerations, limits, revisions));
            return new AiTypes.Context(topic, language, List.copyOf(evidence), List.copyOf(interpretations),
                    List.copyOf(considerations), limits, fingerprint, reason);
        }
    }
}
