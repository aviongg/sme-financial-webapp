package com.app.sme_health_backend.counterparty;

import com.app.sme_health_backend.configuration.dto.ConfigurationRequest;
import com.app.sme_health_backend.configuration.model.OperatingProfile;
import com.app.sme_health_backend.configuration.service.BusinessConfigurationService;
import com.app.sme_health_backend.configuration.service.ModuleAccessPolicy;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.testsupport.ProductApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Counterparty writes (create, update, archive, restore) are rejected server-side with 403 module_disabled when the
 * transactions module is disabled, without mutating anything, while list/detail reads of retained contacts stay
 * available. Role permissions, tenant isolation and the never-configured default are unchanged.
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class CounterpartyModuleEnforcementPostgreSqlIT extends ProductApiTestBase {
    private static final String BASE = "/api/finance/counterparties";

    @Autowired BusinessConfigurationService configuration;
    @Autowired ModuleAccessPolicy modules;

    private void configure(Actor a, List<String> requested, long version) {
        configuration.update(new BusinessAccessContext(a.fixture().user().getId(), a.businessId(), MembershipRole.OWNER),
                new ConfigurationRequest(OperatingProfile.SERVICES, Map.of(), requested, version));
    }

    private Map<String, Object> row(UUID id) {
        return jdbc.queryForMap("SELECT display_name, status, version, updated_at FROM counterparties WHERE id = ?", id);
    }

    private long count(UUID business) {
        return jdbc.queryForObject("SELECT count(*) FROM counterparties WHERE business_id = ?", Long.class, business);
    }

    private static void assertModuleDisabled(JsonNode body) {
        assertEquals("module_disabled", body.get("error").asText(), body.toString());
    }

    @Test
    void writesAreRejectedWithoutMutationWhenTransactionsIsDisabledAndReadsStayAvailable() throws Exception {
        Actor owner = owner();
        UUID active = counterparty(owner, "Synthetic Customer", "CUSTOMER");
        UUID archived = counterparty(owner, "Synthetic Supplier", "SUPPLIER");
        postJson(owner, BASE + "/" + archived + "/archive", null, null, 200);

        configure(owner, List.of("core_finance"), 0L);
        assertFalse(modules.isEnabled(owner.businessId(), "transactions"));
        long before = count(owner.businessId());
        Map<String, Object> activeBefore = row(active);
        Map<String, Object> archivedBefore = row(archived);
        assertEquals("ARCHIVED", archivedBefore.get("status"));

        assertModuleDisabled(postJson(owner, BASE, map("displayName", "Blocked", "type", "CUSTOMER"), null, 403));
        assertModuleDisabled(putJson(owner, BASE + "/" + active,
                map("version", activeBefore.get("version"), "displayName", "Renamed", "type", "CUSTOMER"), 403));
        assertModuleDisabled(postJson(owner, BASE + "/" + active + "/archive", null, null, 403));
        assertModuleDisabled(postJson(owner, BASE + "/" + archived + "/restore", null, null, 403));

        assertEquals(before, count(owner.businessId()), "A rejected create must not insert");
        assertEquals(activeBefore, row(active), "Rejected update/archive must not mutate");
        assertEquals(archivedBefore, row(archived), "Rejected restore must not mutate");

        // Retained contacts stay readable: list (active and archived) and detail.
        assertTrue(getJson(owner, BASE).toString().contains(active.toString()));
        assertTrue(getJson(owner, BASE + "?status=ARCHIVED").toString().contains(archived.toString()));
        assertEquals("Synthetic Customer", getJson(owner, BASE + "/" + active).get("displayName").asText());
        assertEquals("ARCHIVED", getJson(owner, BASE + "/" + archived).get("status").asText());

        // Re-enabling restores every write.
        configure(owner, List.of("core_finance", "transactions"), 1L);
        counterparty(owner, "Allowed Again", "CUSTOMER");
        putJson(owner, BASE + "/" + active,
                map("version", activeBefore.get("version"), "displayName", "Renamed", "type", "CUSTOMER"), 200);
        postJson(owner, BASE + "/" + active + "/archive", null, null, 200);
        postJson(owner, BASE + "/" + archived + "/restore", null, null, 200);
        assertEquals("Renamed", row(active).get("display_name"));
        assertEquals("ACTIVE", row(archived).get("status"));
    }

    @Test
    void neverConfiguredBusinessKeepsWritesAndRolesAndTenantIsolationAreUnchanged() throws Exception {
        Actor owner = owner();
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM business_configuration WHERE business_id = ?",
                Integer.class, owner.businessId()));
        UUID id = counterparty(owner, "Unconfigured Customer", "CUSTOMER");
        putJson(owner, BASE + "/" + id, map("version", row(id).get("version"), "displayName", "Unconfigured Renamed",
                "type", "CUSTOMER"), 200);
        postJson(owner, BASE + "/" + id + "/archive", null, null, 200);
        postJson(owner, BASE + "/" + id + "/restore", null, null, 200);

        // A viewer can read but not write (a permission rejection, not a module one).
        Actor viewer = actor(owner.businessId(), MembershipRole.VIEWER);
        getJson(viewer, BASE + "/" + id);
        JsonNode denied = postJson(viewer, BASE, map("displayName", "Viewer Write", "type", "CUSTOMER"), null, 403);
        assertNotEquals("module_disabled", denied.path("error").asText());
        postJson(viewer, BASE + "/" + id + "/archive", null, null, 403);

        // Another business can neither read nor change it.
        Actor other = owner();
        getJson(other, BASE + "/" + id, 404);
        postJson(other, BASE + "/" + id + "/archive", null, null, 404);
        assertEquals("ACTIVE", row(id).get("status"));
        assertEquals("Unconfigured Renamed", row(id).get("display_name"));
    }
}
