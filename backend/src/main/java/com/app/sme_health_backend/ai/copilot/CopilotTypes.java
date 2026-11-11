package com.app.sme_health_backend.ai.copilot;

import java.util.List;
import java.util.Map;

/** Provider-neutral Copilot contract (copilot-v1). Providers never see identity, raw rows or database access. */
public final class CopilotTypes {
    private CopilotTypes() { }

    public static final String VERSION = "copilot-v1";
    public static final int MAX_ROUNDS = 4;
    public static final int MAX_TOOL_CALLS = 6;
    public static final int MAX_EVIDENCE_PER_TOOL = 40;

    /** A parameter of a tool: type is "string" or "integer"; enumValues/pattern/maxLength bound strings. */
    public record Param(String name, String type, String description, List<String> enumValues, String pattern,
                        Integer maxLength, Integer min, Integer max, boolean required) { }

    public record ToolSpec(String name, String description, List<Param> params) { }

    public record ToolCall(String callId, String name, Map<String, Object> arguments) { }

    public record Evidence(String id, String tool, String label, String value) { }

    public record ToolResult(String callId, String name, String status, String message, List<Evidence> evidence) { }

    public record Turn(String role, String content) { }

    /** Everything a provider may see for one step. */
    public record Conversation(String language, String question, List<Turn> history, List<ToolSpec> tools,
                               List<ToolCall> priorCalls, List<ToolResult> priorResults, int round) { }

    /** Either tool calls or a final answer citing evidence ids. */
    public record Step(List<ToolCall> toolCalls, String answer, List<String> citedEvidenceIds) {
        public static Step calls(List<ToolCall> calls) {
            return new Step(calls, null, List.of());
        }

        public static Step answer(String text, List<String> cited) {
            return new Step(List.of(), text, cited == null ? List.of() : cited);
        }

        public boolean isFinal() {
            return toolCalls == null || toolCalls.isEmpty();
        }
    }

    public record Response(String version, String language, String answer, List<Evidence> evidence,
                           List<Map<String, Object>> tools, String mode, String model, String reasonCode) { }
}
