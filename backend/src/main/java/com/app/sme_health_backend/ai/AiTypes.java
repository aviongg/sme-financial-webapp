package com.app.sme_health_backend.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

/** The public contract contains only backend-rendered financial values and explanations. */
public final class AiTypes {
    private AiTypes() { }
    public static final String SCHEMA_VERSION = "ai-explanation-v1";
    public static final String PROMPT_VERSION = "evidence-selection-v1";

    public record Turn(@NotNull @Pattern(regexp = "user|assistant") String role,
                       @NotBlank @Size(max = 1000) String content) {
        @com.fasterxml.jackson.annotation.JsonAnySetter
        public void rejectUnknown(String name, Object value) { throw new IllegalArgumentException("Unknown conversation field"); }
    }
    public record Request(@NotNull @Pattern(regexp = "dashboard|insight|recommendation|ask") String type,
                          @NotNull @Pattern(regexp = "en|ur") String language,
                          @Size(max = 1000) String question, UUID sourceId,
                          @Pattern(regexp = "[0-9]{4}-(0[1-9]|1[0-2])") String month,
                          @Size(max = 8) List<@NotNull @Valid Turn> conversation) {
        @com.fasterxml.jackson.annotation.JsonAnySetter
        public void rejectUnknown(String name, Object value) { throw new IllegalArgumentException("Unknown AI request field"); }
    }
    public record Status(boolean enabled, boolean available, String mode, String reasonCode,
                         String model, int maxQuestionLength, int maxConversationTurns) { }
    public record Evidence(String id, String kind, String label, String value, String unit,
                           String period, String sourceMode) { }
    public record Provenance(String mode, String model, String promptVersion,
                             String evidenceFingerprint, boolean cached) { }
    public record Response(String schemaVersion, String language, String summary,
                           List<String> interpretation, List<String> considerations,
                           List<String> limitations, List<Evidence> evidence,
                           Provenance provenance, String reasonCode) {
        Response cached() {
            return new Response(schemaVersion, language, summary, interpretation, considerations,
                    limitations, evidence, new Provenance(provenance.mode, provenance.model,
                    provenance.promptVersion, provenance.evidenceFingerprint, true), reasonCode);
        }
    }
    /** A provider can select/order approved clauses, never supply numbers or free financial prose. */
    public record Clause(String id, String text, List<String> evidenceIds) { }
    public record Context(String topic, String language, List<Evidence> evidence,
                          List<Clause> interpretations, List<Clause> considerations,
                          List<String> limitationCodes, String fingerprint, String unavailableReason) { }
    public record Selection(String schemaVersion, String language, String summaryCode,
                            List<String> interpretationIds, List<String> considerationIds,
                            List<String> limitationCodes) { }
}
