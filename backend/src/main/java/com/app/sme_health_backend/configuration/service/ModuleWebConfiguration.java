package com.app.sme_health_backend.configuration.service;

import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class ModuleWebConfiguration {
    @Bean
    WebMvcConfigurer moduleOperationGuards(BusinessAuthorizationService authorization, ModuleAccessPolicy modules) {
        return new WebMvcConfigurer() {
            @Override public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new org.springframework.web.servlet.HandlerInterceptor() {
                    @Override public boolean preHandle(jakarta.servlet.http.HttpServletRequest request,
                            jakarta.servlet.http.HttpServletResponse response, Object handler) {
                        String method = request.getMethod();
                        if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) return true;
                        String path = request.getRequestURI().substring(request.getContextPath().length());
                        // The established filter/query endpoint reads retained history despite using POST.
                        if ("POST".equals(method) && "/api/documents/query".equals(path)) return true;
                        String module = path.startsWith("/api/documents") ? "documents" : "whatsapp";
                        modules.requireEnabled(authorization.requireActiveContext(request).businessId(), module);
                        return true;
                    }
                }).addPathPatterns("/api/documents", "/api/documents/**", "/api/profile/whatsapp", "/api/whatsapp/**");
            }
        };
    }
}
