package com.app.sme_health_backend.inventory;

import com.app.sme_health_backend.configuration.service.ModuleWebConfiguration;
import com.app.sme_health_backend.inventory.controller.InventoryItemController;
import com.app.sme_health_backend.inventory.ledger.InventoryLedgerController;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DEF-22 contract: every state-changing route of the stock/inventory controllers is gated by the inventory module, and
 * read routes are not. Derived from the controllers' own mappings so a new stock write path cannot slip through.
 */
class InventoryWriteRouteModuleContractTest {

    record Route(String method, String path) { }

    static List<Route> routes(Class<?> controller) {
        RequestMapping base = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
        assertNotNull(base, controller.getSimpleName());
        List<Route> routes = new ArrayList<>();
        for (Method method : controller.getDeclaredMethods()) {
            RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
            if (mapping == null) continue;
            String[] paths = mapping.path().length == 0 ? new String[] {""} : mapping.path();
            for (RequestMethod verb : mapping.method()) {
                for (String prefix : base.path()) {
                    for (String path : paths) {
                        routes.add(new Route(verb.name(), (prefix + path).replaceAll("\\{[^}]+}", "00000000-0000-0000-0000-000000000001")));
                    }
                }
            }
        }
        return routes;
    }

    @Test
    void everyStockWriteRouteRequiresInventoryAndReadsDoNot() {
        List<Route> all = new ArrayList<>(routes(InventoryLedgerController.class));
        all.addAll(routes(InventoryItemController.class));
        long writes = 0;
        for (Route route : all) {
            Optional<String> module = ModuleWebConfiguration.moduleFor(route.method(), route.path());
            if (route.method().equals("GET")) {
                assertEquals(Optional.empty(), module, "Stock history read must stay available: " + route);
            } else {
                writes++;
                assertEquals(Optional.of("inventory"), module, "Stock write not gated by inventory: " + route);
            }
        }
        assertTrue(writes >= 12, "Expected the stock movement, document and item write routes, found " + writes);
    }
}
