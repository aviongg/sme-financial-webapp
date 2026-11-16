package com.app.sme_health_backend.whatsapp.controller;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.shared.exception.GlobalExceptionHandler;
import com.app.sme_health_backend.whatsapp.client.WhatsAppClient;
import com.app.sme_health_backend.whatsapp.dto.WhatsAppDeliveryCapabilityResponse;
import com.app.sme_health_backend.whatsapp.scheduler.WhatsAppSummaryScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** DEF-08: the outbound capability contract is read from the wired client, never from history or consent. */
@WebMvcTest(WhatsAppDeliveryCapabilityController.class)
@Import(GlobalExceptionHandler.class)
class WhatsAppDeliveryCapabilityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WhatsAppClient outboundClient;

    @MockitoBean
    private WhatsAppSummaryScheduler scheduler;

    @MockitoBean
    private BusinessAuthorizationService authService;

    @BeforeEach
    void setUp() {
        when(authService.requirePermission(any(), any(BusinessPermission.class)))
                .thenReturn(new BusinessAccessContext(UUID.randomUUID(), UUID.randomUUID(), MembershipRole.VIEWER));
    }

    @Test
    void reportsTheWiredOutboundProviderAndSchedulerStateOnly() throws Exception {
        for (String provider : new String[]{"mock", "disabled", "meta"}) {
            when(outboundClient.getProviderName()).thenReturn(provider);
            when(scheduler.isAutomaticDeliveryActive()).thenReturn(!"disabled".equals(provider));
            mockMvc.perform(get("/api/whatsapp/delivery-capability"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.provider").value(provider))
                    .andExpect(jsonPath("$.automaticWeeklySummaries").value(!"disabled".equals(provider)))
                    .andExpect(jsonPath("$.length()").value(2));
        }
        verify(authService, org.mockito.Mockito.atLeastOnce()).requirePermission(any(), eq(BusinessPermission.FINANCIAL_DATA_READ));
    }

    @Test
    void anUnrecognisedOrMissingProviderIsUnknownNeverAssumedLive() throws Exception {
        for (String provider : new String[]{"twilio", "", " META-ish "}) {
            when(outboundClient.getProviderName()).thenReturn(provider);
            mockMvc.perform(get("/api/whatsapp/delivery-capability"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.provider").value("unknown"));
        }
        assertThat(WhatsAppDeliveryCapabilityResponse.of(null, false).provider()).isEqualTo("unknown");
        assertThat(WhatsAppDeliveryCapabilityResponse.of(" Mock ", true).provider()).isEqualTo("mock");
    }

    @Test
    void requiresTheSameReadPermissionAsDeliveryHistoryAndRevealsNothingWhenDenied() throws Exception {
        when(authService.requirePermission(any(), eq(BusinessPermission.FINANCIAL_DATA_READ)))
                .thenThrow(new AccessDeniedException("denied"));
        mockMvc.perform(get("/api/whatsapp/delivery-capability")).andExpect(status().isForbidden());
        verify(outboundClient, never()).getProviderName();
        verify(outboundClient, never()).sendSummary(any());
    }

    @Test
    void schedulerReportsActiveOnlyWhenEnabledAndAProviderIsConfigured() {
        assertThat(scheduler(true, "mock").isAutomaticDeliveryActive()).isTrue();
        assertThat(scheduler(true, "meta").isAutomaticDeliveryActive()).isTrue();
        assertThat(scheduler(true, "disabled").isAutomaticDeliveryActive()).isFalse();
        assertThat(scheduler(false, "mock").isAutomaticDeliveryActive()).isFalse();
        assertThat(scheduler(false, "meta").isAutomaticDeliveryActive()).isFalse();
    }

    private static WhatsAppSummaryScheduler scheduler(boolean enabled, String provider) {
        return new WhatsAppSummaryScheduler(mock(com.app.sme_health_backend.whatsapp.service.WhatsAppDeliveryService.class),
                mock(com.app.sme_health_backend.profile.repository.BusinessProfileRepository.class),
                mock(com.app.sme_health_backend.whatsapp.recovery.WhatsAppDeliveryRecovery.class), enabled, provider);
    }
}
