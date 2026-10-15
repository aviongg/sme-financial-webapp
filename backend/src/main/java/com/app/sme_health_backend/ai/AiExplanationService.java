package com.app.sme_health_backend.ai;

import com.app.sme_health_backend.audit.model.AuditEventType;
import com.app.sme_health_backend.audit.model.AuditOutcome;
import com.app.sme_health_backend.audit.service.SecurityAuditService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class AiExplanationService {
    private static final Logger LOG = LoggerFactory.getLogger(AiExplanationService.class);
    private final AiAvailability availability;
    private final AiProperties properties;
    private final AiQuestionRouter router;
    private final MvpFinancialEvidenceAdapter evidence;
    private final SimulatedAiProvider simulated;
    private final OpenAiResponsesProvider openai;
    private final AiGroundingValidator validator;
    private final AiResourceLimits limits;
    private final SecurityAuditService audit;
    public AiExplanationService(AiAvailability availability, AiProperties properties, AiQuestionRouter router,
            MvpFinancialEvidenceAdapter evidence, SimulatedAiProvider simulated, OpenAiResponsesProvider openai,
            AiGroundingValidator validator, AiResourceLimits limits, SecurityAuditService audit) {
        this.availability = availability; this.properties = properties; this.router = router; this.evidence = evidence;
        this.simulated = simulated; this.openai = openai; this.validator = validator; this.limits = limits; this.audit = audit;
    }
    public AiTypes.Response explain(BusinessAccessContext actor, AiTypes.Request request) {
        long start = System.nanoTime();
        AiTypes.Response result = generate(actor, router.normalize(request));
        String reason = result.reasonCode() == null ? "completed" : result.reasonCode();
        Map<String, Object> metadata = Map.of("type", request.type(), "mode", result.provenance().mode(),
                "result", reason, "promptVersion", AiTypes.PROMPT_VERSION, "cacheHit", result.provenance().cached(),
                "latencyMs", (System.nanoTime() - start) / 1_000_000);
        audit.recordSecurityEvent(AuditEventType.AI_EXPLANATION_REQUESTED, actor.userId(), null, actor.businessId(),
                "ai_explanation", null, result.reasonCode() == null ? AuditOutcome.SUCCESS : AuditOutcome.FAILURE, null, metadata);
        LOG.info("AI explanation type={} mode={} result={} cacheHit={}", request.type(), result.provenance().mode(), reason, result.provenance().cached());
        return result;
    }
    private AiTypes.Response generate(BusinessAccessContext actor, AiTypes.Request request) {
        String topic = router.topic(request);
        AiTypes.Context empty = empty(topic, request.language());
        String requestLimit = limits.request(actor.businessId());
        if (requestLimit != null) return fallback(empty, requestLimit);
        var status = availability.status(actor.businessId());
        if (!status.available()) return fallback(empty, status.reasonCode());
        if ("unsupported".equals(topic)) return fallback(empty, "unsupported_question");
        AiTypes.Context context = evidence.build(actor, request, topic);
        if (context.unavailableReason() != null) return fallback(context, context.unavailableReason());
        String key = AiJson.hash(Arrays.asList(actor.businessId(), actor.userId(), actor.role(), request.type(),
                request.language(), request.sourceId(), request.month(), context.fingerprint(),
                AiTypes.SCHEMA_VERSION, AiTypes.PROMPT_VERSION, status.mode(), properties.getModel()));
        var cached = limits.cached(key);
        if (cached != null) return cached;
        String quota = limits.beginProvider(actor.businessId());
        if (quota != null) return fallback(context, quota);
        boolean succeeded = false;
        try {
            String output = "simulated".equals(status.mode()) ? simulated.generate(context) : openai.generate(context);
            var selection = validator.validate(output, context);
            succeeded = true;
            var currentStatus = availability.status(actor.businessId());
            if (!currentStatus.available() || !status.mode().equals(currentStatus.mode())
                    || !Objects.equals(status.model(), currentStatus.model()))
                return fallback(evidence.build(actor, request, topic), "provider_configuration_changed");
            // Never deliver/cache a provider response for a snapshot edited during its request.
            var current = evidence.build(actor, request, topic);
            if (!current.fingerprint().equals(context.fingerprint())) return fallback(current, "evidence_changed");
            var response = validator.render(context, selection, status.mode(), status.model(), null);
            limits.cache(key, response); return response;
        } catch (OpenAiResponsesProvider.ProviderFailure e) {
            return fallback(evidence.build(actor, request, topic), e.code());
        } catch (IllegalArgumentException e) {
            return fallback(evidence.build(actor, request, topic), "invalid_provider_response");
        } finally { limits.finishProvider(actor.businessId(), succeeded); }
    }
    private AiTypes.Response fallback(AiTypes.Context context, String reason) {
        return validator.render(context, SimulatedAiProvider.selection(context), "deterministic", null, reason);
    }
    private static AiTypes.Context empty(String topic, String language) {
        return new AiTypes.Context(topic, language, List.of(), List.of(), List.of(),
                List.of("unsupported_modules"), AiJson.hash(List.of(topic, language, "no_evidence")), null);
    }
}
