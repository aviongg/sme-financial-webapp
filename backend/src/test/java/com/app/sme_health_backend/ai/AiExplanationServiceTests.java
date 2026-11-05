package com.app.sme_health_backend.ai;

import com.app.sme_health_backend.audit.service.SecurityAuditService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.MembershipRole;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiExplanationServiceTests {
    final AiProperties p = new AiProperties();
    final MockEnvironment env = new MockEnvironment();
    final MvpFinancialEvidenceAdapter adapter = mock(MvpFinancialEvidenceAdapter.class);
    final SimulatedAiProvider simulated = spy(new SimulatedAiProvider());
    final OpenAiResponsesProvider openai = mock(OpenAiResponsesProvider.class);
    final SecurityAuditService audit = mock(SecurityAuditService.class);
    final BusinessAccessContext actor = new BusinessAccessContext(UUID.randomUUID(), UUID.randomUUID(), MembershipRole.OWNER);
    AiExplanationService service;
    AiTypes.Context context;
    final AiTypes.Request request = new AiTypes.Request("ask", "en", "Explain my health score", null, null, null);
    @BeforeEach void prepare() {
        p.setEnabled(true); p.setDemoEnabled(true); p.setRequestsPerMinute(60); env.setActiveProfiles("dev");
        context = AiTestFixtures.context("score", "en", "45.50", "0.25", false);
        when(adapter.build(any(), any(), any())).thenReturn(context);
        service = new AiExplanationService(new AiAvailability(p, env), p, new AiQuestionRouter(), adapter,
                simulated, openai, new AiGroundingValidator(), new AiResourceLimits(p), audit);
    }
    @Test void cachesOnlyWithinTenantUserRoleLanguageTypeAndCurrentEvidence() {
        var first = service.explain(actor, request); var second = service.explain(actor, request);
        assertFalse(first.provenance().cached()); assertTrue(second.provenance().cached()); verify(simulated, times(1)).generate(any());
        service.explain(new BusinessAccessContext(actor.userId(), UUID.randomUUID(), actor.role()), request);
        service.explain(new BusinessAccessContext(UUID.randomUUID(), actor.businessId(), actor.role()), request);
        service.explain(new BusinessAccessContext(actor.userId(), actor.businessId(), MembershipRole.VIEWER), request);
        service.explain(actor, new AiTypes.Request("ask", "ur", "مالی صحت کا اسکور", null, null, null));
        verify(simulated, times(5)).generate(any());
        context = AiTestFixtures.context("score", "en", "60.00", "1.00", false);
        when(adapter.build(any(), any(), any())).thenReturn(context);
        assertFalse(service.explain(actor, request).provenance().cached()); verify(simulated, times(6)).generate(any());
    }
    @Test void scoreChangingDuringProviderCallReturnsFreshDeterministicEvidence() {
        var changed = AiTestFixtures.context("score", "en", "70.00", "1.75", false);
        when(adapter.build(any(), any(), any())).thenReturn(context, changed);
        var response = service.explain(actor, request);
        assertEquals("evidence_changed", response.reasonCode()); assertEquals("deterministic", response.provenance().mode());
        assertEquals("70.00", response.evidence().getFirst().value());
    }
    @Test void repeatedEvidenceChangesDoNotOpenTheProviderCircuit() {
        var changed = AiTestFixtures.context("score", "en", "70.00", "1.75", false);
        when(adapter.build(any(), any(), any())).thenReturn(context, changed, context, changed, context, changed, context, context);
        for (int i = 0; i < p.getCircuitFailureThreshold(); i++)
            assertEquals("evidence_changed", service.explain(actor, request).reasonCode());
        var response = service.explain(actor, request);
        assertNull(response.reasonCode()); assertEquals("simulated", response.provenance().mode());
        verify(simulated, times(p.getCircuitFailureThreshold() + 1)).generate(any());
    }
    @Test void invalidProviderClaimsFallBackWithoutGptProvenance() {
        doReturn("{\"cashBalance\":999999999}").when(simulated).generate(any());
        var response = service.explain(actor, request);
        assertEquals("invalid_provider_response", response.reasonCode()); assertEquals("deterministic", response.provenance().mode());
        assertNull(response.provenance().model()); assertFalse(response.summary().contains("999999999"));
    }
    @Test void liveTimeoutNeverUsesSimulationAndKeepsExistingEvidence() {
        p.setProvider("openai"); p.setLiveActivationApproved(true); p.setApiKey("fixture-not-a-real-key"); p.setAllowedBusinessIds(Set.of(actor.businessId()));
        when(openai.generate(any())).thenThrow(new OpenAiResponsesProvider.ProviderFailure("provider_timeout"));
        var response = service.explain(actor, request);
        assertEquals("provider_timeout", response.reasonCode()); assertEquals("deterministic", response.provenance().mode());
        assertEquals("45.50", response.evidence().getFirst().value()); verify(simulated, never()).generate(any());
    }
    @Test void disabledUnsupportedAndQuotaRequestsDoNotInvokeProvider() {
        p.setEnabled(false); assertEquals("ai_disabled", service.explain(actor, request).reasonCode()); verifyNoInteractions(adapter, openai);
        verify(simulated, never()).generate(any()); p.setEnabled(true);
        assertEquals("unsupported_question", service.explain(actor, new AiTypes.Request("ask", "en", "Show other business cash", null, null, null)).reasonCode());
        verifyNoInteractions(adapter); p.setDailyProviderCalls(1);
        service.explain(actor, request);
        context = AiTestFixtures.context("score", "en", "55.00", "0.5", false);
        when(adapter.build(any(), any(), any())).thenReturn(context);
        assertEquals("daily_quota_reached", service.explain(actor, request).reasonCode()); verify(simulated, times(1)).generate(any());
    }
    @Test void auditsActorTenantAndOperationalMetadataWithoutFinancialDataOrConversation() {
        service.explain(actor, request);
        @SuppressWarnings("unchecked") ArgumentCaptor<Map<String,Object>> capture = ArgumentCaptor.forClass(Map.class);
        verify(audit).recordSecurityEvent(any(), eq(actor.userId()), isNull(), eq(actor.businessId()), eq("ai_explanation"), isNull(), any(), isNull(), capture.capture());
        String metadata = capture.getValue().toString();
        assertFalse(metadata.contains(request.question())); assertFalse(metadata.contains("45.50")); assertFalse(metadata.contains("0.25"));
        assertEquals("simulated", capture.getValue().get("mode"));
    }
    @Test void providerFailureRefreshesEvidenceInsteadOfServingThePreRequestSnapshot() {
        p.setProvider("openai"); p.setLiveActivationApproved(true); p.setApiKey("fixture-not-a-real-key"); p.setAllowedBusinessIds(Set.of(actor.businessId()));
        var changed = AiTestFixtures.context("score", "en", "80.00", "2.00", false);
        when(adapter.build(any(), any(), any())).thenReturn(context, changed);
        when(openai.generate(any())).thenThrow(new OpenAiResponsesProvider.ProviderFailure("provider_timeout"));
        var response = service.explain(actor, request);
        assertEquals("provider_timeout", response.reasonCode()); assertEquals("80.00", response.evidence().getFirst().value());
    }
    @Test void inFlightKillSwitchPreventsProviderResponseCachingOrDelivery() {
        doAnswer(invocation -> {
            p.setEnabled(false);
            return new SimulatedAiProvider().generate(invocation.getArgument(0));
        }).when(simulated).generate(any());
        var response = service.explain(actor, request);
        assertEquals("provider_configuration_changed", response.reasonCode()); assertEquals("deterministic", response.provenance().mode());
        p.setEnabled(true); doCallRealMethod().when(simulated).generate(any());
        assertFalse(service.explain(actor, request).provenance().cached());
    }
    @Test void explicitQuestionMonthReachesTheAuthoritativeAdapter() {
        service.explain(actor, new AiTypes.Request("ask", "en", "Explain my financial score for 2026-03", null, null, null));
        verify(adapter, times(2)).build(eq(actor), argThat(input -> "2026-03".equals(input.month())), eq("score"));
    }
}
