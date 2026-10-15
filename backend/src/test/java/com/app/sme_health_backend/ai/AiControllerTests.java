package com.app.sme_health_backend.ai;

import com.app.sme_health_backend.configuration.service.ModuleAccessPolicy;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.*;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AiControllerTests {
    final BusinessAuthorizationService auth = mock(BusinessAuthorizationService.class);
    final ModuleAccessPolicy modules = mock(ModuleAccessPolicy.class);
    final AiAvailability availability = mock(AiAvailability.class);
    final AiExplanationService service = mock(AiExplanationService.class);
    final BusinessAccessContext actor = new BusinessAccessContext(UUID.randomUUID(), UUID.randomUUID(), MembershipRole.VIEWER);
    MockMvc mvc;
    @BeforeEach void setup() {
        when(auth.requirePermission(any(), eq(BusinessPermission.FINANCIAL_DATA_READ))).thenReturn(actor);
        when(modules.isEnabled(actor.businessId(), "ai")).thenReturn(true);
        var context = AiTestFixtures.context("score", "en", "55", "0.5", false);
        when(service.explain(eq(actor), any())).thenReturn(new AiGroundingValidator().render(context, SimulatedAiProvider.selection(context), "simulated", null, null));
        mvc = MockMvcBuilders.standaloneSetup(new AiController(auth, modules, availability, service))
                .setControllerAdvice(new AiRequestExceptionHandler(), new GlobalExceptionHandler()).build();
    }
    @Test void derivesActorAndTenantFromSessionAndChecksModuleTwice() throws Exception {
        mvc.perform(post("/api/ai/explain").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"ask\",\"language\":\"en\",\"question\":\"Explain health score\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        verify(modules, times(2)).requireEnabled(actor.businessId(), "ai");
    }
    @Test void deniesUnauthorizedAndDisabledModuleWithoutEvidenceAccess() throws Exception {
        doThrow(new AccessDeniedException("Disabled")).when(modules).requireEnabled(actor.businessId(), "ai");
        mvc.perform(post("/api/ai/explain").contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"dashboard\",\"language\":\"en\"}"))
                .andExpect(status().isForbidden()); verifyNoInteractions(service);
    }
    @Test void invalidatesResultWhenBusinessChangesWhileProviderRuns() throws Exception {
        when(auth.requirePermission(any(), eq(BusinessPermission.FINANCIAL_DATA_READ))).thenReturn(actor,
                new BusinessAccessContext(actor.userId(), UUID.randomUUID(), MembershipRole.OWNER));
        mvc.perform(post("/api/ai/explain").contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"dashboard\",\"language\":\"en\"}"))
                .andExpect(status().isForbidden());
    }
    @Test void rejectsFinancialResultWhenModuleIsDisabledDuringProviderCall() throws Exception {
        doNothing().doThrow(new AccessDeniedException("Disabled")).when(modules).requireEnabled(actor.businessId(), "ai");
        mvc.perform(post("/api/ai/explain").contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"dashboard\",\"language\":\"en\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.evidence").doesNotExist());
        verify(service).explain(eq(actor), any());
    }
    @Test void rejectsFinancialResultWhenRoleChangesDuringProviderCall() throws Exception {
        when(auth.requirePermission(any(), eq(BusinessPermission.FINANCIAL_DATA_READ))).thenReturn(actor,
                new BusinessAccessContext(actor.userId(), actor.businessId(), MembershipRole.MANAGER));
        mvc.perform(post("/api/ai/explain").contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"dashboard\",\"language\":\"en\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.evidence").doesNotExist());
    }
    @Test void requiresScopedSourceIdentifiersForSourceExplanation() throws Exception {
        mvc.perform(post("/api/ai/explain").contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"insight\",\"language\":\"en\"}"))
                .andExpect(status().isBadRequest()); verifyNoInteractions(service);
    }
    @Test void rejectsOversizedQuestionHistoryAndUnknownTenantField() throws Exception {
        for (String body : List.of("{\"type\":\"ask\",\"language\":\"en\",\"question\":\"" + "x".repeat(1001) + "\"}",
                "{\"type\":\"dashboard\",\"language\":\"fr\"}",
                "{\"type\":\"dashboard\",\"language\":\"en\",\"businessId\":\"" + UUID.randomUUID() + "\"}"))
            mvc.perform(post("/api/ai/explain").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void requestBodyFilterBoundsPayloadBeforeJsonParsing() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/ai/explain"); request.setServletPath("/api/ai/explain");
        request.setContent(new byte[50000]); var response = new MockHttpServletResponse();
        var chain = new MockFilterChain(); new AiRequestSizeFilter().doFilter(request, response, chain);
        assertEquals(413, response.getStatus()); assertNull(chain.getRequest());
    }
    @Test void bodyLimitAlsoAppliesWhenContentLengthIsUnknown() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/ai/explain") {
            @Override public long getContentLengthLong() { return -1; }
            @Override public int getContentLength() { return -1; }
        };
        request.setServletPath("/api/ai/explain"); request.setContent(new byte[50000]);
        var response = new MockHttpServletResponse(); var chain = new MockFilterChain();
        new AiRequestSizeFilter().doFilter(request, response, chain);
        assertEquals(413, response.getStatus()); assertNull(chain.getRequest());
    }
}
