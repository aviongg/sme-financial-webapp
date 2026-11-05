package com.app.sme_health_backend.inventory.service;

import java.math.RoundingMode;
import java.util.Objects;

/**
 * Rounding parameters for the F08 weighted-average-cost calculation, kept in one place so they are
 * never spread through the code. {@link #DRAFT} is the reviewed architecture's draft proposal; it is NOT
 * frozen accounting policy, and every result produced with it is stamped non-authoritative.
 *
 * @param version            stamped on every preview response
 * @param valueScale         maximum scale of accepted values and of a computed issue value
 * @param valueRounding      rounding of a partial-issue value V * q / Q
 * @param averageDisplayScale scale of the derived, display-only average unit cost V / Q
 * @param approved           whether this policy is approved accounting policy (never true in Phase A)
 */
public record WacPolicy(
        String version,
        int valueScale,
        RoundingMode valueRounding,
        int averageDisplayScale,
        boolean approved
) {
    public static final WacPolicy DRAFT = new WacPolicy("f08-wac-draft-1", 4, RoundingMode.HALF_EVEN, 6, false);

    public WacPolicy {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(valueRounding, "valueRounding");
        if (valueScale < 0 || averageDisplayScale < 0) {
            throw new IllegalArgumentException("Scales must be non-negative");
        }
        if (valueRounding == RoundingMode.UNNECESSARY) {
            throw new IllegalArgumentException("A partial issue requires an explicit rounding mode");
        }
    }
}
