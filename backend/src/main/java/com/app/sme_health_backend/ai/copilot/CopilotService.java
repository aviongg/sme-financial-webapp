package com.app.sme_health_backend.ai.copilot;

import com.app.sme_health_backend.ai.AiAvailability;
import com.app.sme_health_backend.ai.AiResourceLimits;
import com.app.sme_health_backend.ai.copilot.CopilotTypes.Evidence;
import com.app.sme_health_backend.ai.copilot.CopilotTypes.Step;
import com.app.sme_health_backend.ai.copilot.CopilotTypes.ToolCall;
import com.app.sme_health_backend.ai.copilot.CopilotTypes.ToolResult;
import com.app.sme_health_backend.audit.model.AuditEventType;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.productsupport.AuditRecorder;
import com.app.sme_health_backend.productsupport.DomainException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Copilot orchestration: bounded tool-calling loop, per-call authorization (registry), rate/size limits, audit,
 * and a hard grounding gate. A final answer is delivered only if every number it contains appears in tool evidence
 * (or in the user's own question), every cited id exists, and it contains no links; otherwise the deterministic,
 * evidence-only rendering is returned with reason "ungrounded_answer".
 */
@Service
public class CopilotService {
    private static final Pattern NUMBER = Pattern.compile("-?\\d[\\d,]*(?:\\.\\d+)?");
    private static final Pattern LINK = Pattern.compile("(?i)(https?://|www\\.|!\\[|\\]\\()");

    private final CopilotToolRegistry registry;
    private final DeterministicCopilotProvider deterministic;
    private final OpenAiCopilotProvider openai;
    private final AiAvailability availability;
    private final AiResourceLimits limits;
    private final AuditRecorder audit;

    public CopilotService(CopilotToolRegistry registry, DeterministicCopilotProvider deterministic, OpenAiCopilotProvider openai,
                          AiAvailability availability, AiResourceLimits limits, AuditRecorder audit) {
        this.registry = registry;
        this.deterministic = deterministic;
        this.openai = openai;
        this.availability = availability;
        this.limits = limits;
        this.audit = audit;
    }

    public CopilotTypes.Response ask(BusinessAccessContext ctx, String question, String language, List<CopilotTypes.Turn> history) {
        String q = question == null ? "" : question.strip();
        if (q.isEmpty() || q.length() > 1000) throw DomainException.invalid("question", "length_invalid");
        if (!"en".equals(language) && !"ur".equals(language)) throw DomainException.invalid("language", "enum_invalid");
        List<CopilotTypes.Turn> turns = history == null ? List.of() : history;
        if (turns.size() > 8) throw DomainException.invalid("conversation", "too_many_turns");
        for (CopilotTypes.Turn t : turns) {
            if (t == null || !("user".equals(t.role()) || "assistant".equals(t.role())) || t.content() == null
                    || t.content().isBlank() || t.content().length() > 1000) {
                throw DomainException.invalid("conversation", "turn_invalid");
            }
        }
        String limited = limits.request(ctx.businessId());
        if (limited != null) {
            return new CopilotTypes.Response(CopilotTypes.VERSION, language, DeterministicCopilotProvider.unsupported(language),
                    List.of(), List.of(), "deterministic", null, limited);
        }
        var status = availability.status(ctx.businessId());
        CopilotProvider provider = status.available() && "openai".equals(status.mode()) ? openai : deterministic;
        String statusReason = status.available() ? null : status.reasonCode();
        return run(ctx, provider, q, language, turns, statusReason);
    }

    /** Package-visible for provider-substitution security tests. */
    CopilotTypes.Response run(BusinessAccessContext ctx, CopilotProvider provider, String question, String language,
                              List<CopilotTypes.Turn> history, String statusReason) {
        List<ToolCall> calls = new ArrayList<>();
        List<ToolResult> results = new ArrayList<>();
        String reason = statusReason;
        Step finalStep = null;
        boolean external = !(provider instanceof DeterministicCopilotProvider);
        for (int round = 0; round <= CopilotTypes.MAX_ROUNDS; round++) {
            Step step;
            String quota = external ? limits.beginProvider(ctx.businessId()) : null;
            if (quota != null) {
                reason = quota;
                break;
            }
            boolean ok = false;
            try {
                step = provider.next(new CopilotTypes.Conversation(language, question, history, registry.specs(ctx),
                        List.copyOf(calls), List.copyOf(results), round));
                ok = true;
            } catch (CopilotProvider.ProviderFailure f) {
                reason = f.code();
                break;
            } catch (RuntimeException e) {
                reason = "provider_unavailable";
                break;
            } finally {
                if (external) limits.finishProvider(ctx.businessId(), ok);
            }
            if (step.isFinal()) {
                finalStep = step;
                break;
            }
            if (round == CopilotTypes.MAX_ROUNDS || calls.size() + step.toolCalls().size() > CopilotTypes.MAX_TOOL_CALLS) {
                reason = "tool_budget_exceeded";
                break;
            }
            for (ToolCall call : step.toolCalls()) {
                ToolResult result = registry.execute(ctx, call);
                calls.add(call);
                results.add(result);
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("tool", String.valueOf(call.name()).length() > 60 ? String.valueOf(call.name()).substring(0, 60) : String.valueOf(call.name()));
                meta.put("status", result.status());
                meta.put("message", result.message() == null ? "" : result.message());
                meta.put("provider", provider.mode());
                audit.record(ctx, AuditEventType.COPILOT_TOOL_INVOKED, "COPILOT_TOOL", null, meta);
            }
        }

        List<Evidence> allEvidence = results.stream().flatMap(r -> r.evidence().stream()).toList();
        String answer;
        List<String> cited;
        String mode = provider.mode();
        if (finalStep != null && grounded(finalStep, allEvidence, question)) {
            answer = finalStep.answer().strip();
            cited = finalStep.citedEvidenceIds();
        } else {
            if (finalStep != null) reason = "ungrounded_answer";
            Step fallback = results.isEmpty() ? Step.answer(DeterministicCopilotProvider.unsupported(language), List.of())
                    : DeterministicCopilotProvider.render(language, results);
            answer = fallback.answer();
            cited = fallback.citedEvidenceIds();
            mode = "deterministic";
        }
        Set<String> citedSet = new HashSet<>(cited);
        List<Evidence> shown = allEvidence.stream().filter(e -> citedSet.contains(e.id())).toList();
        if (shown.isEmpty()) shown = allEvidence.size() > 30 ? allEvidence.subList(0, 30) : allEvidence;
        List<Map<String, Object>> toolSummary = results.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", r.name());
            m.put("status", r.status());
            m.put("message", r.message());
            return m;
        }).toList();
        return new CopilotTypes.Response(CopilotTypes.VERSION, language, answer, shown, toolSummary, mode,
                "openai".equals(mode) ? provider.model() : null, reason);
    }

    /** The grounding gate (also unit-tested directly). */
    static boolean grounded(Step step, List<Evidence> evidence, String question) {
        String answer = step.answer();
        if (answer == null || answer.isBlank() || answer.length() > 4000) return false;
        if (LINK.matcher(answer).find()) return false;
        Set<String> ids = new HashSet<>();
        List<BigDecimal> allowed = new ArrayList<>();
        for (Evidence e : evidence) {
            ids.add(e.id());
            numbers(e.value(), allowed);
        }
        numbers(question, allowed);
        for (String id : step.citedEvidenceIds()) {
            if (!ids.contains(id)) return false;
        }
        List<BigDecimal> used = new ArrayList<>();
        numbers(answer, used);
        if (!used.isEmpty() && step.citedEvidenceIds().isEmpty()) return false;
        for (BigDecimal n : used) {
            boolean smallOrdinal = n.signum() >= 0 && n.compareTo(BigDecimal.TEN) <= 0 && n.stripTrailingZeros().scale() <= 0;
            if (smallOrdinal) continue;
            if (allowed.stream().noneMatch(a -> a.compareTo(n) == 0 || a.abs().compareTo(n.abs()) == 0)) return false;
        }
        return true;
    }

    private static void numbers(String text, List<BigDecimal> out) {
        if (text == null) return;
        Matcher m = NUMBER.matcher(toAsciiDigits(text));
        while (m.find()) {
            try {
                out.add(new BigDecimal(m.group().replace(",", "")));
            } catch (NumberFormatException ignored) {
                // not a number after normalization
            }
        }
    }

    private static String toAsciiDigits(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if (c >= '۰' && c <= '۹') sb.append((char) ('0' + (c - '۰')));
            else if (c >= '٠' && c <= '٩') sb.append((char) ('0' + (c - '٠')));
            else sb.append(c);
        }
        return sb.toString();
    }
}
