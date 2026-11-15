package com.app.sme_health_backend.counterparty;

import com.app.sme_health_backend.configuration.service.ModuleWebConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Counterparty (customer/supplier master data) writes are gated by the transactions module; reads are not, so retained
 * contacts stay visible when the module is off. Derived from the controller's own mappings so a new write cannot slip
 * through ungated.
 */
class CounterpartyWriteRouteModuleContractTest {

    record Route(String method, String path) { }

    private static final String ID = "00000000-0000-0000-0000-000000000001";

    static List<Route> routes() {
        RequestMapping base = AnnotatedElementUtils.findMergedAnnotation(CounterpartyController.class, RequestMapping.class);
        assertNotNull(base);
        List<Route> routes = new ArrayList<>();
        for (Method method : CounterpartyController.class.getDeclaredMethods()) {
            RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
            if (mapping == null) continue;
            String[] paths = mapping.path().length == 0 ? new String[] {""} : mapping.path();
            for (RequestMethod verb : mapping.method()) {
                for (String prefix : base.path()) {
                    for (String path : paths) {
                        routes.add(new Route(verb.name(), (prefix + path).replaceAll("\\{[^}]+}", ID)));
                    }
                }
            }
        }
        return routes;
    }

    @Test
    void everyCounterpartyWriteRequiresTransactionsAndReadsDoNot() {
        List<Route> writes = new ArrayList<>();
        int reads = 0;
        for (Route route : routes()) {
            Optional<String> module = ModuleWebConfiguration.moduleFor(route.method(), route.path());
            if (route.method().equals("GET")) {
                reads++;
                assertEquals(Optional.empty(), module, "Counterparty read must stay available: " + route);
            } else {
                writes.add(route);
                assertEquals(Optional.of("transactions"), module, "Counterparty write not gated by transactions: " + route);
            }
        }
        Comparator<Route> order = Comparator.comparing(Route::toString);
        assertEquals(List.of(
                        new Route("POST", "/api/finance/counterparties"),
                        new Route("PUT", "/api/finance/counterparties/" + ID),
                        new Route("POST", "/api/finance/counterparties/" + ID + "/archive"),
                        new Route("POST", "/api/finance/counterparties/" + ID + "/restore")).stream().sorted(order).toList(),
                writes.stream().sorted(order).toList(),
                "create, update, archive and restore are the counterparty writes");
        assertEquals(2, reads, "list and detail reads");
    }
}
