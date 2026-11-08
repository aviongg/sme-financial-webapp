package com.app.sme_health_backend.fx.model;

/**
 * Distinguishes authoritative verification levels of initial obligation recognition rates.
 */
public enum RecognitionProvenance {
    VERIFIED_RECOGNITION_EVIDENCE,
    USER_DECLARED_RECOGNITION,
    HISTORICAL_REFERENCE,
    UNAVAILABLE;

    public boolean isAuthoritative() {
        return this == VERIFIED_RECOGNITION_EVIDENCE;
    }

    public static RecognitionProvenance fromStringOrDefault(String value) {
        if (value == null || value.isBlank()) {
            return UNAVAILABLE;
        }
        try {
            return RecognitionProvenance.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            // Map legacy or freeform provenance strings to structured classifications
            String upper = value.trim().toUpperCase();
            if (upper.contains("VERIFIED") || upper.contains("CONTRACT") || upper.contains("INVOICE")) {
                return VERIFIED_RECOGNITION_EVIDENCE;
            } else if (upper.contains("HISTORICAL") || upper.contains("CENTRAL_BANK") || upper.contains("MARKET")) {
                return HISTORICAL_REFERENCE;
            } else if (upper.contains("DECLARATION") || upper.contains("USER") || upper.contains("MANUAL")) {
                return USER_DECLARED_RECOGNITION;
            }
            return USER_DECLARED_RECOGNITION;
        }
    }
}

