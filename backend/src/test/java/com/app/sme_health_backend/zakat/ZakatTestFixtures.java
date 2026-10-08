package com.app.sme_health_backend.zakat;

import com.app.sme_health_backend.zakat.dto.ZakatPreviewRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static com.app.sme_health_backend.zakat.dto.ZakatPreviewRequest.*;
import static com.app.sme_health_backend.zakat.dto.ZakatTypes.*;

final class ZakatTestFixtures {
    private ZakatTestFixtures() {
    }

    static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    static final class Fixture {
        LocalDate date = LocalDate.of(2026, 9, 30);
        String currency = "PKR";
        String metal = "SILVER";
        BigDecimal weight = money("612.36");
        BigDecimal price = money("100");
        String priceSource = "Test quotation";
        OffsetDateTime priceTimestamp = OffsetDateTime.parse("2026-09-30T12:00:00+05:00");
        HaulStatus haul = HaulStatus.CONFIRMED;
        BigDecimal cash = money("100000");
        List<InventoryItem> inventory = List.of();
        List<Receivable> receivables = List.of();
        List<String> unsupported = List.of();
        BigDecimal accountsPayable = BigDecimal.ZERO;
        List<LiabilityItem> payables = List.of();
        List<LiabilityItem> principal = List.of();
        Boolean noOverlap;
        String financingType = "none";
        BigDecimal loan = BigDecimal.ZERO;
        BigDecimal interest = BigDecimal.ZERO;

        ZakatPreviewRequest request() {
            return new ZakatPreviewRequest(assessment(),
                    new Assets(cash, inventory, receivables, unsupported),
                    new Liabilities(accountsPayable, payables, principal, noOverlap),
                    new Financing(financingType, loan, interest));
        }

        Assessment assessment() {
            return new Assessment(date, currency, metal, weight, price, priceSource, priceTimestamp, haul);
        }
    }
}
