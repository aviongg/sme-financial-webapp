package com.app.sme_health_backend.records.derived;

import org.junit.jupiter.api.Test;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract: the C11 Financial Query Facade is strictly read-only, so none of its endpoints may trigger V52 projection
 * materialisation; the product surfaces that present materialised months keep refreshing on read.
 *
 * <p>Regression: with a valid stored score fixture, {@code FinancialQueryPostgreSqlIT
 * .testReadOnlyDatabaseValueLevelSnapshotInvariants} saw {@code monthly_records} grow from 1 to 3 after facade GETs,
 * because {@code /api/finance/query/**} was registered as a freshness path. The facade's own methods read ledger,
 * obligation and stored-score tables directly and wrote nothing; the interceptor was the only writer.
 */
class ProjectionFreshnessPathContractTest {

    private static final List<PathPattern> PATTERNS = Arrays.stream(MonthlyProjectionWebConfiguration.READ_PATHS)
            .map(PathPatternParser.defaultInstance::parse).toList();

    private static boolean refreshes(String path) {
        PathContainer container = PathContainer.parsePath(path);
        return PATTERNS.stream().anyMatch(p -> p.matches(container));
    }

    @Test
    void financialQueryFacadeEndpointsNeverMaterialise() {
        for (String endpoint : List.of("capabilities", "cash-position", "overdue-receivables", "upcoming-obligations",
                "project-financials", "project-funding-gap", "margin-drivers", "fx-settlement-impact", "health-evidence")) {
            assertThat(refreshes("/api/finance/query/" + endpoint)).as(endpoint).isFalse();
        }
    }

    @Test
    void productSurfacesPresentingMaterialisedMonthsStillRefresh() {
        for (String path : List.of("/api/records/monthly", "/api/records/monthly/2026-10", "/api/scores/latest",
                "/api/reports/pack", "/api/dashboard/summary", "/api/copilot/ask", "/api/zakat/preview")) {
            assertThat(refreshes(path)).as(path).isTrue();
        }
    }
}
