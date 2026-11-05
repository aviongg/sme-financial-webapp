package com.app.sme_health_backend.ai;

/** Receives only a bounded, server-selected evidence catalog; no identity or conversation text. */
public interface AiProvider {
    String generate(AiTypes.Context context);
}
