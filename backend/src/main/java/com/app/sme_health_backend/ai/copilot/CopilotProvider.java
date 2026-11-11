package com.app.sme_health_backend.ai.copilot;

/** A Copilot model adapter: decides the next step from tool specs, the question and prior tool evidence. */
public interface CopilotProvider {
    String mode();

    String model();

    CopilotTypes.Step next(CopilotTypes.Conversation conversation);

    /** Typed provider failure; the Copilot falls back to deterministic grounded output. */
    final class ProviderFailure extends RuntimeException {
        private final String code;

        public ProviderFailure(String code) {
            super(code);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
