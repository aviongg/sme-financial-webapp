package com.app.sme_health_backend.whatsapp.dto;

import java.util.Locale;
import java.util.Set;

/**
 * DEF-08: the outbound WhatsApp summary transport actually wired into this running backend. It describes the
 * environment's configuration only; it never claims a message was transmitted or delivered (each delivery record
 * carries its own provider and status). Contains no credentials, phone numbers or business data.
 *
 * @param provider                 {@code meta}, {@code mock} (simulated, nothing transmitted), {@code disabled}
 *                                 (nothing sent) or {@code unknown} for any unrecognised client
 * @param automaticWeeklySummaries whether the weekly scheduler would attempt sends in this environment
 */
public record WhatsAppDeliveryCapabilityResponse(String provider, boolean automaticWeeklySummaries) {

    private static final Set<String> KNOWN = Set.of("meta", "mock", "disabled");

    public static WhatsAppDeliveryCapabilityResponse of(String providerName, boolean automaticWeeklySummaries) {
        String name = providerName == null ? "" : providerName.trim().toLowerCase(Locale.ROOT);
        return new WhatsAppDeliveryCapabilityResponse(KNOWN.contains(name) ? name : "unknown", automaticWeeklySummaries);
    }
}
