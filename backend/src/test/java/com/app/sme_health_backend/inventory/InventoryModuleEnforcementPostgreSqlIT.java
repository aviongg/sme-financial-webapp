package com.app.sme_health_backend.inventory;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DEF-22: stock (inventory ledger) writes must be rejected server-side when the inventory module is disabled, while
 * retained stock history stays readable (retained-history policy). Every stock write is served under /api/inventory
 * (InventoryLedgerController / InventoryItemController; the UI's "/inventory/stock" is a page route, not an API).
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class InventoryModuleEnforcementPostgreSqlIT extends ProductApiTestBase {
    @Autowired BusinessConfigurationService configuration;
    @Autowired ModuleAccessPolicy modules;

    private BusinessAccessContext ownerContext(Actor a) {
        return new BusinessAccessContext(a.fixture().user().getId(), a.businessId(), MembershipRole.OWNER);
    }

    private long movementCount(UUID business) {
        return jdbc.queryForObject("SELECT count(*) FROM inventory_movements WHERE business_id = ?", Long.class, business);
    }

    @Test
    void stockWritesAreRejectedWhenInventoryIsDisabledAndHistoryStaysReadable() throws Exception {
        Actor owner = owner();
        UUID item = item(owner, "DEF22-1");
        JsonNode opening = postJson(owner, "/api/inventory/movements/opening",
                map("itemId", item, "quantity", "5", "value", "50.00"), key(), 201);
        String movement = opening.get("id").asText();
        long before = movementCount(owner.businessId());

        // Explicitly configure without inventory (transactions stays on, so only the inventory module is disabled).
        configuration.update(ownerContext(owner), new ConfigurationRequest(OperatingProfile.RETAIL_TRADE, Map.of(),
                List.of("core_finance", "transactions"), 0L));
        assertFalse(modules.isEnabled(owner.businessId(), "inventory"));

        UUID unknown = UUID.randomUUID();
        Map<String, Object> body = map("itemId", item, "quantity", "1", "value", "10.00", "purpose", "SALE",
                "direction", "INCREASE", "reason", "count", "issueMovementId", movement);
        for (String path : List.of("/api/inventory/movements/opening", "/api/inventory/movements/receipts",
                "/api/inventory/movements/issues", "/api/inventory/movements/supplier-returns",
                "/api/inventory/movements/returns", "/api/inventory/movements/adjustments",
                "/api/inventory/movements/" + movement + "/reverse",
                "/api/inventory/documents/bills/" + unknown + "/receive",
                "/api/inventory/documents/invoices/" + unknown + "/fulfil",
                "/api/inventory/items", "/api/inventory/items/" + item + "/archive")) {
            mvc.perform(post(path).cookie(owner.session()).with(csrf(owner.session())).header("Idempotency-Key", key())
                            .contentType("application/json").content(json.writeValueAsString(body)))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("module_disabled"));
        }
        mvc.perform(put("/api/inventory/items/" + item).cookie(owner.session()).with(csrf(owner.session()))
                        .contentType("application/json").content(json.writeValueAsString(map("name", "Renamed"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("module_disabled"));
        assertEquals(before, movementCount(owner.businessId()), "A rejected stock write must not move stock");

        // Retained history: register, item, movement history, valuation and COGS stay readable.
        boolean inRegister = false;
        for (JsonNode row : getJson(owner, "/api/inventory/register").get("items")) {
            inRegister |= row.get("itemId").asText().equals(item.toString());
        }
        assertTrue(inRegister, "Retained item must stay visible in the stock register");
        getJson(owner, "/api/inventory/items");
        getJson(owner, "/api/inventory/items/" + item);
        assertTrue(getJson(owner, "/api/inventory/items/" + item + "/movements").toString().contains(movement));
        assertTrue(getJson(owner, "/api/inventory/movements").toString().contains(movement));
        getJson(owner, "/api/inventory/valuation");
        getJson(owner, "/api/inventory/cogs");

        // Re-enabling restores writes.
        configuration.update(ownerContext(owner), new ConfigurationRequest(OperatingProfile.RETAIL_TRADE, Map.of(),
                List.of("core_finance", "transactions", "inventory"), 1L));
        postJson(owner, "/api/inventory/movements/receipts", map("itemId", item, "quantity", "1", "value", "10.00"), key(), 201);
        assertEquals(before + 1, movementCount(owner.businessId()));
    }
}
