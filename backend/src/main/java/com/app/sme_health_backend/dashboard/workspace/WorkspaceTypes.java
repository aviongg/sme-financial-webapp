package com.app.sme_health_backend.dashboard.workspace;

import com.app.sme_health_backend.ai.AiTypes;
import com.app.sme_health_backend.cashflow.dto.CashFlowChartPointResponse;
import com.app.sme_health_backend.dashboard.dto.DashboardResponse;
import com.app.sme_health_backend.records.dto.MonthlyRecordResponse;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class WorkspaceTypes {
    private WorkspaceTypes() { }
    public record Preference(@NotBlank @Size(max = 48) String key, @NotNull Boolean hidden,
            @NotNull @Min(0) @Max(100) Integer order, @NotBlank @Size(max = 16) String size) {
        @JsonAnySetter public void rejectUnknown(String key, Object value) { throw new IllegalArgumentException("Unknown widget field"); }
    }
    public record SaveRequest(@NotNull @PositiveOrZero Long version, @NotBlank String registryVersion,
            @NotNull @PositiveOrZero Long configurationVersion,
            @NotNull @Size(max = 40) List<@NotNull @Valid Preference> widgets) {
        @JsonAnySetter public void rejectUnknown(String key, Object value) { throw new IllegalArgumentException("Unknown workspace field"); }
    }
    public record ResetRequest(@NotNull @PositiveOrZero Long version, @NotBlank String registryVersion,
            @NotNull @PositiveOrZero Long configurationVersion) {
        @JsonAnySetter public void rejectUnknown(String key, Object value) { throw new IllegalArgumentException("Unknown workspace field"); }
    }
    public record Widget(String key, int order, String size) { }
    public record Metadata(String key, String module, boolean implemented, boolean required,
            List<String> sizes, boolean available, String reason) { }
    public record CashSummary(BigDecimal totalInflow, BigDecimal totalOutflow, BigDecimal net,
            BigDecimal endingCash, String fromMonth, String toMonth, int recordCount) { }
    public record Response(String registryVersion, String presetVersion, String presetKey, String recommendedPresetKey,
            long preferenceVersion, long configurationVersion, boolean personalized,
            List<Widget> widgets, List<Preference> preferences, List<Metadata> catalog,
            DashboardResponse dashboard, List<MonthlyRecordResponse> records,
            List<CashFlowChartPointResponse> cashHistory, Map<String, CashSummary> cashSummaryByPeriod,
            String sourceMode, Instant generatedAt, AiTypes.Status aiStatus) { }
}
