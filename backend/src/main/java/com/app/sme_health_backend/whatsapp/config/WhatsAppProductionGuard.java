package com.app.sme_health_backend.whatsapp.config;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Fails startup instead of letting production report simulated WhatsApp traffic as delivered.
 *
 * <p>Under the {@code prod}/{@code production} profile the only accepted providers are {@code disabled} (the
 * default: nothing is sent and the scheduler skips) and {@code meta} with both Cloud API credentials present. The
 * in-memory {@code mock} provider fabricates provider message ids and is refused, mirroring how the in-memory
 * password-reset notifier fails closed in production.
 */
@Component
public class WhatsAppProductionGuard {

    public WhatsAppProductionGuard(Environment environment) {
        verify(environment);
    }

    static void verify(Environment environment) {
        if (!environment.acceptsProfiles(Profiles.of("prod | production"))) {
            return;
        }
        String provider = environment.getProperty("app.whatsapp.provider", "mock").trim().toLowerCase(Locale.ROOT);
        switch (provider) {
            case "disabled" -> { }
            case "meta" -> {
                if (blank(environment.getProperty("app.whatsapp.meta.phone-number-id"))
                        || blank(environment.getProperty("app.whatsapp.meta.access-token"))) {
                    throw new IllegalStateException("WHATSAPP_PROVIDER=meta requires WHATSAPP_META_PHONE_NUMBER_ID and "
                            + "WHATSAPP_META_ACCESS_TOKEN in production; set WHATSAPP_PROVIDER=disabled to run without WhatsApp.");
                }
            }
            default -> throw new IllegalStateException("WhatsApp provider '" + provider + "' is not allowed in production; "
                    + "use 'meta' (with credentials) or 'disabled'.");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
