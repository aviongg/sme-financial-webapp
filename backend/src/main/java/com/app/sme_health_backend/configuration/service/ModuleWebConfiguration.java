package com.app.sme_health_backend.configuration.service;

import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Server-side module enforcement for state-changing requests (F02/C03: "role/module rules apply both on direct API and
 * navigation"). Reads stay available so a disabled module's retained data can still be viewed and exported; role
 * permissions are checked by each handler as before. See {@link ModuleAccessPolicy} for the backward-compatible rule
 * for businesses that never saved a configuration.
 */
@Configuration
public class ModuleWebConfiguration {

    /** API prefix → module key. Longest matching prefix wins. */
    static final Map<String, String> WRITE_MODULES;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("/api/documents", "documents");
        m.put("/api/profile/whatsapp", "whatsapp");
        m.put("/api/whatsapp", "whatsapp");
        m.put("/api/finance/transactions", "transactions");
        m.put("/api/finance/transfers", "transactions");
        m.put("/api/finance/accounts", "transactions");
        m.put("/api/finance/drafts", "transactions");
        m.put("/api/finance/recurring", "transactions");
        m.put("/api/finance/reconciliation", "transactions");
        m.put("/api/finance/obligations", "receivables");
        m.put("/api/finance/payments", "receivables");
        m.put("/api/finance/advances", "receivables");
        m.put("/api/invoices", "invoices");
        m.put("/api/bills", "invoices");
        m.put("/api/commercial", "invoices");
        m.put("/api/projects", "projects");
        m.put("/api/finance/projects", "projects");
        m.put("/api/estimates", "costing");
        m.put("/api/inventory", "inventory");
        m.put("/api/samples", "production");
        m.put("/api/finance/commissions", "commissions");
        m.put("/api/finance/salespersons", "commissions");
        m.put("/api/finance/fx", "multi_currency");
        WRITE_MODULES = Map.copyOf(m);
    }

    public static Optional<String> moduleFor(String method, String path) {
        if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) return Optional.empty();
        // The established filter/query endpoint reads retained history despite using POST.
        if ("POST".equals(method) && "/api/documents/query".equals(path)) return Optional.empty();
        // The signed Meta webhook has no session; WhatsAppInboundService checks the module per business.
        if (path.equals("/api/whatsapp/webhook") || path.startsWith("/api/whatsapp/webhook/")) return Optional.empty();
        String best = null;
        for (String prefix : WRITE_MODULES.keySet()) {
            if ((path.equals(prefix) || path.startsWith(prefix + "/")) && (best == null || prefix.length() > best.length())) {
                best = prefix;
            }
        }
        return Optional.ofNullable(best).map(WRITE_MODULES::get);
    }

    @Bean
    WebMvcConfigurer moduleOperationGuards(BusinessAuthorizationService authorization, ModuleAccessPolicy modules) {
        return new WebMvcConfigurer() {
            @Override public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new org.springframework.web.servlet.HandlerInterceptor() {
                    @Override public boolean preHandle(jakarta.servlet.http.HttpServletRequest request,
                            jakarta.servlet.http.HttpServletResponse response, Object handler) {
                        String path = request.getRequestURI().substring(request.getContextPath().length());
                        Optional<String> module = moduleFor(request.getMethod(), path);
                        if (module.isPresent()) {
                            modules.requireEnabled(authorization.requireActiveContext(request).businessId(), module.get());
                        }
                        return true;
                    }
                }).addPathPatterns("/api/**");
            }
        };
    }
}
