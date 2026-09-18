package com.app.sme_health_backend.zakat.policy;

import java.math.BigDecimal;

public record ZakatPolicy(
        String ruleProfile,
        String ruleVersion,
        String nisabMetal,
        BigDecimal metalWeightGrams,
        BigDecimal zakatRate,
        String debtPolicy
) {
    public static final ZakatPolicy HANAFI_PK_BUSINESS_V1 = new ZakatPolicy(
            "HANAFI_PK_BUSINESS_V1", "1.0.0", "SILVER",
            new BigDecimal("612.36"), new BigDecimal("0.025"),
            "CURRENT_PAYABLES_AND_PRINCIPAL_DUE_WITHIN_12_LUNAR_MONTHS"
    );

    public static final ZakatPolicy ACTIVE_PROFILE = HANAFI_PK_BUSINESS_V1;

    public static final String DISCLOSURE =
            "A calculation based on the selected Zakat rule profile and information provided. "
                    + "Complex or disputed cases may require review by a qualified Islamic scholar.";
}
