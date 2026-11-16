package com.app.sme_health_backend.whatsapp.capability;

import com.app.sme_health_backend.configuration.dto.ConfigurationRequest;
import com.app.sme_health_backend.configuration.model.OperatingProfile;
import com.app.sme_health_backend.configuration.service.BusinessConfigurationService;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.testsupport.ProductApiTestBase;
import com.app.sme_health_backend.whatsapp.client.WhatsAppClient;
import com.app.sme_health_backend.whatsapp.scheduler.WhatsAppSummaryScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DEF-08 Candidate-v3 blocker: with an EMPTY delivery history the outbound WhatsApp capability is still disclosed,
 * from the actual {@link WhatsAppClient} bean selected by {@code app.whatsapp.provider} in a real Spring runtime on
 * disposable PostgreSQL. Each subclass boots one real provider configuration. The signal is never derived from consent,
 * the module toggle or delivery rows; reading it sends and changes nothing; role permissions are unchanged.
 */
abstract class WhatsAppOutboundCapabilityContract extends ProductApiTestBase {
    static final String CAPABILITY = "/api/whatsapp/delivery-capability";
    static final String DELIVERIES = "/api/whatsapp/deliveries";

    @Autowired WhatsAppClient outboundClient;
    @Autowired WhatsAppSummaryScheduler scheduler;
    @Autowired BusinessConfigurationService configuration;

    /** The provider name the configured runtime must report. */
    abstract String expectedProvider();

    /** The outbound client class the configuration must wire. */
    abstract Class<? extends WhatsAppClient> expectedClient();

    private int deliveryRows() {
        return jdbc.queryForObject("SELECT count(*) FROM whatsapp_deliveries", Integer.class);
    }

    private void assertCapability(JsonNode body) {
        Set<String> names = new HashSet<>();
        body.propertyNames().forEach(names::add);
        assertEquals(Set.of("provider", "automaticWeeklySummaries"), names, body.toString());
        assertEquals(expectedProvider(), body.get("provider").asText(), body.toString());
        // These runtimes keep the scheduler off, so none of them may claim automatic sending.
        assertFalse(body.get("automaticWeeklySummaries").asBoolean(), body.toString());
    }

    private void setConsent(Actor a, boolean optIn) throws Exception {
        mvc.perform(patch("/api/profile/whatsapp").cookie(a.session()).with(csrf(a.session())).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("whatsappNumber", "+923001234567", "optIn", optIn))))
                .andExpect(status().isOk());
    }

    @Test
    void emptyHistoryStillDisclosesTheActuallyWiredOutboundProvider() throws Exception {
        assertInstanceOf(expectedClient(), outboundClient, "The configured provider selects this outbound client");
        assertEquals(expectedProvider(), outboundClient.getProviderName());
        assertFalse(scheduler.isAutomaticDeliveryActive());

        Actor owner = owner();
        int before = deliveryRows();
        assertEquals(0, getJson(owner, DELIVERIES).size(), "Delivery history is empty");
        assertCapability(getJson(owner, CAPABILITY));
        assertEquals(before, deliveryRows(), "Reading the capability sends nothing and records no delivery");
    }

    @Test
    void capabilityIsNotInferredFromConsentOrModuleToggleAndChangesNothing() throws Exception {
        Actor owner = owner();
        assertCapability(getJson(owner, CAPABILITY));

        setConsent(owner, true);
        assertTrue(profiles.findById(owner.businessId()).orElseThrow().isWhatsappOptIn());
        assertCapability(getJson(owner, CAPABILITY));

        configuration.update(new BusinessAccessContext(owner.fixture().user().getId(), owner.businessId(), MembershipRole.OWNER),
                new ConfigurationRequest(OperatingProfile.SERVICES, Map.of(), List.of("core_finance"), 0L));
        assertCapability(getJson(owner, CAPABILITY));

        assertTrue(profiles.findById(owner.businessId()).orElseThrow().isWhatsappOptIn(), "Consent is unchanged by reads");
        assertEquals(0, getJson(owner, DELIVERIES).size());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM whatsapp_deliveries WHERE user_id = ?", Integer.class,
                owner.businessId()));
    }

    @Test
    void readRolesSeeTheSameEnvironmentSignalAndPermissionsAndIsolationAreUnchanged() throws Exception {
        Actor owner = owner();
        for (MembershipRole role : List.of(MembershipRole.ACCOUNTANT, MembershipRole.MANAGER, MembershipRole.VIEWER)) {
            assertCapability(getJson(actor(owner.businessId(), role), CAPABILITY));
        }
        // SALESPERSON has no FINANCIAL_DATA_READ: denied exactly like the delivery history.
        Actor salesperson = actor(owner.businessId(), MembershipRole.SALESPERSON);
        getJson(salesperson, CAPABILITY, 403);
        getJson(salesperson, DELIVERIES, 403);

        // Another business sees the same environment signal and nothing about this business.
        Actor other = owner();
        JsonNode otherBody = getJson(other, CAPABILITY);
        assertCapability(otherBody);
        assertFalse(otherBody.toString().contains(owner.businessId().toString()));

        mvc.perform(get(CAPABILITY)).andExpect(status().isUnauthorized());
        // Read-only: no write verb is mapped, and a write attempt changes nothing.
        postJson(owner, CAPABILITY, Map.of("provider", "meta"), null, 405);
        assertCapability(getJson(owner, CAPABILITY));
    }

    @Test
    void responseCarriesNoCredentialsOrPhoneNumbers() throws Exception {
        Actor owner = owner();
        setConsent(owner, true);
        String body = getJson(owner, CAPABILITY).toString();
        for (String secret : List.of("synthetic-meta-token", "123456789012345", "+92300", "4567", "graph.facebook", "127.0.0.1")) {
            assertFalse(body.contains(secret), secret);
        }
    }
}
