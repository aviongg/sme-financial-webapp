package com.app.sme_health_backend.records.derived;

import com.app.sme_health_backend.identity.service.ActiveBusinessContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.UUID;

/**
 * Keeps materialised monthly projections fresh on every read path that consumes {@code monthly_records} or
 * {@code score_results}, so a report, dashboard, score, Copilot answer or Zakat preview never shows a month that
 * predates an authoritative change. The refresh runs in its own transaction before the handler and is a single
 * indexed lookup when nothing changed.
 */
@Configuration
@EnableScheduling
public class MonthlyProjectionWebConfiguration {

    static final String[] READ_PATHS = {
            "/api/records/monthly", "/api/records/monthly/**", "/api/scores/**", "/api/reports/**",
            "/api/dashboard/**", "/api/workspace/**", "/api/cashflow/**", "/api/zakat/**", "/api/insights/**",
            "/api/recommendations/**", "/api/finance/query/**", "/api/copilot/**", "/api/ai/**", "/api/search/**",
            "/api/planning/**", "/api/intelligence/**"
    };

    @Bean
    WebMvcConfigurer monthlyProjectionFreshness(ActiveBusinessContext activeBusiness, MonthlyProjectionService projections) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new FreshnessInterceptor(activeBusiness, projections)).addPathPatterns(READ_PATHS)
                        // The explicit rebuild performs (and reports) the refresh itself.
                        .excludePathPatterns("/api/records/monthly/recalculate");
            }
        };
    }

    static final class FreshnessInterceptor implements HandlerInterceptor {
        private static final Logger log = LoggerFactory.getLogger(FreshnessInterceptor.class);
        private final ActiveBusinessContext activeBusiness;
        private final MonthlyProjectionService projections;

        FreshnessInterceptor(ActiveBusinessContext activeBusiness, MonthlyProjectionService projections) {
            this.activeBusiness = activeBusiness;
            this.projections = projections;
        }

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            UUID businessId;
            try {
                businessId = activeBusiness.getCurrentContext(request).map(c -> c.businessId()).orElse(null);
            } catch (RuntimeException e) {
                // Authentication/authorisation is enforced by the handler itself; freshness never changes that outcome.
                log.debug("No active business for projection freshness: {}", e.getClass().getSimpleName());
                return true;
            }
            if (businessId != null) {
                projections.refreshIfStale(businessId);
            }
            return true;
        }
    }

    /** Background safety net: refreshes dirty businesses nobody is viewing and rolls months over. */
    @Component
    static class Sweeper {
        private static final Logger log = LoggerFactory.getLogger(Sweeper.class);
        private final MonthlyProjectionService projections;
        private final boolean enabled;

        Sweeper(MonthlyProjectionService projections,
                @Value("${app.monthly-projection.sweeper.enabled:true}") boolean enabled) {
            this.projections = projections;
            this.enabled = enabled;
        }

        @Scheduled(fixedDelayString = "${app.monthly-projection.sweeper.interval-ms:300000}",
                initialDelayString = "${app.monthly-projection.sweeper.interval-ms:300000}")
        void sweep() {
            if (!enabled) {
                return;
            }
            for (UUID businessId : projections.staleBusinesses(50)) {
                projections.refreshIfStale(businessId);
            }
            log.debug("Monthly projection sweep complete");
        }
    }
}
