package com.app.sme_health_backend.fx;

import com.app.sme_health_backend.fx.dto.FxPreviewRequest;
import com.app.sme_health_backend.fx.dto.FxPreviewResponse;
import com.app.sme_health_backend.fx.exception.FxValidationException;
import com.app.sme_health_backend.fx.model.FxDirection;
import com.app.sme_health_backend.fx.model.RateSourceType;
import com.app.sme_health_backend.fx.service.CurrencyMetadataService;
import com.app.sme_health_backend.fx.service.FxCalculationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FxCalculationServiceTest {

    private FxCalculationService calculationService;

    @BeforeEach
    void setUp() {
        CurrencyMetadataService currencyMetadataService = new CurrencyMetadataService();
        calculationService = new FxCalculationService(currencyMetadataService);
    }

    @Test
    @DisplayName("Case A: Outgoing USD 100 with PKR 150 fee -> PKR 28,000 debit")
    void testCaseA_OutgoingPaymentWithFee() {
        // Base: USD, Quote: PKR, Rate: 278.50, Principal: 100 USD, Direction: OUTGOING, Fee: 150 PKR
        FxPreviewRequest request = new FxPreviewRequest(
                "USD",
                "PKR",
                new BigDecimal("278.50"),
                FxDirection.OUTGOING,
                new BigDecimal("100.00"),
                "USD",
                new BigDecimal("150.00"),
                "PKR",
                BigDecimal.ZERO,
                null,
                RateSourceType.MANUAL_USER_RATE
        );

        FxPreviewResponse response = calculationService.previewConversion(request);

        assertThat(response.baseCurrency()).isEqualTo("USD");
        assertThat(response.quoteCurrency()).isEqualTo("PKR");
        assertThat(response.settlementCurrency()).isEqualTo("PKR");
        assertThat(response.grossSettlementAmount()).isEqualByComparingTo(new BigDecimal("27850.00"));
        assertThat(response.totalFeeAmount()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(response.totalSettlementDebited()).isEqualByComparingTo(new BigDecimal("28000.00"));
        assertThat(response.netSettlementAmount()).isEqualByComparingTo(new BigDecimal("27850.00"));
        assertThat(response.effectiveRate()).isEqualByComparingTo(new BigDecimal("280.000000"));
        assertThat(response.provenance().calculationType()).isEqualTo("PURE_JAVA_DETERMINISTIC_PREVIEW");
    }

    @Test
    @DisplayName("Case B: Incoming USD 100 with PKR 150 fee -> PKR 27,700 net receipt")
    void testCaseB_IncomingReceiptWithFee() {
        // Base: USD, Quote: PKR, Rate: 278.50, Principal: 100 USD, Direction: INCOMING, Fee: 150 PKR
        FxPreviewRequest request = new FxPreviewRequest(
                "USD",
                "PKR",
                new BigDecimal("278.50"),
                FxDirection.INCOMING,
                new BigDecimal("100.00"),
                "USD",
                new BigDecimal("150.00"),
                "PKR",
                BigDecimal.ZERO,
                null,
                RateSourceType.MANUAL_USER_RATE
        );

        FxPreviewResponse response = calculationService.previewConversion(request);

        assertThat(response.grossSettlementAmount()).isEqualByComparingTo(new BigDecimal("27850.00"));
        assertThat(response.totalFeeAmount()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(response.netSettlementAmount()).isEqualByComparingTo(new BigDecimal("27700.00"));
        assertThat(response.totalSettlementDebited()).isEqualByComparingTo(new BigDecimal("27850.00"));
        assertThat(response.effectiveRate()).isEqualByComparingTo(new BigDecimal("277.000000"));
    }

    @Test
    @DisplayName("Case C: JPY 0 minor-unit and KWD 3 minor-unit precision")
    void testCaseC_PrecisionHandling() {
        // 1. USD to JPY (JPY minorUnit = 0)
        FxPreviewRequest jpyReq = new FxPreviewRequest(
                "USD",
                "JPY",
                new BigDecimal("155.3456"),
                FxDirection.OUTGOING,
                new BigDecimal("100.00"),
                "USD",
                BigDecimal.ZERO,
                "JPY",
                BigDecimal.ZERO,
                null,
                RateSourceType.MANUAL_USER_RATE
        );
        FxPreviewResponse jpyResp = calculationService.previewConversion(jpyReq);
        // 100 * 155.3456 = 15534.56 -> rounds to 15535 JPY
        assertThat(jpyResp.grossSettlementAmount()).isEqualByComparingTo(new BigDecimal("15535"));
        assertThat(jpyResp.grossSettlementAmount().scale()).isEqualTo(0);

        // 2. USD to KWD (KWD minorUnit = 3)
        FxPreviewRequest kwdReq = new FxPreviewRequest(
                "USD",
                "KWD",
                new BigDecimal("0.307255"),
                FxDirection.INCOMING,
                new BigDecimal("100.00"),
                "USD",
                BigDecimal.ZERO,
                "KWD",
                BigDecimal.ZERO,
                null,
                RateSourceType.MANUAL_USER_RATE
        );
        FxPreviewResponse kwdResp = calculationService.previewConversion(kwdReq);
        // 100 * 0.307255 = 30.7255 -> rounds HALF_UP to 30.726 KWD
        assertThat(kwdResp.grossSettlementAmount()).isEqualByComparingTo(new BigDecimal("30.726"));
        assertThat(kwdResp.grossSettlementAmount().scale()).isEqualTo(3);
    }

    @Test
    @DisplayName("Case D: Rate variation scenario variance analysis")
    void testCaseD_RateVariationScenarioVariance() {
        // Baseline USD/PKR 278.50 vs Comparison USD/PKR 282.00
        FxPreviewRequest reqOutgoing = new FxPreviewRequest(
                "USD",
                "PKR",
                new BigDecimal("278.50"),
                FxDirection.OUTGOING,
                new BigDecimal("100.00"),
                "USD",
                BigDecimal.ZERO,
                "PKR",
                BigDecimal.ZERO,
                new BigDecimal("282.00"),
                RateSourceType.MANUAL_USER_RATE
        );
        FxPreviewResponse respOutgoing = calculationService.previewConversion(reqOutgoing);

        assertThat(respOutgoing.scenarioVariance()).isNotNull();
        assertThat(respOutgoing.scenarioVariance().comparisonRate()).isEqualByComparingTo(new BigDecimal("282.00"));
        assertThat(respOutgoing.scenarioVariance().comparisonGrossAmount()).isEqualByComparingTo(new BigDecimal("28200.00"));
        assertThat(respOutgoing.scenarioVariance().varianceAmount()).isEqualByComparingTo(new BigDecimal("350.00"));
        assertThat(respOutgoing.scenarioVariance().impact()).isEqualTo("UNFAVORABLE"); // Outgoing cost is higher

        // Now test for INCOMING
        FxPreviewRequest reqIncoming = new FxPreviewRequest(
                "USD",
                "PKR",
                new BigDecimal("278.50"),
                FxDirection.INCOMING,
                new BigDecimal("100.00"),
                "USD",
                BigDecimal.ZERO,
                "PKR",
                BigDecimal.ZERO,
                new BigDecimal("282.00"),
                RateSourceType.MANUAL_USER_RATE
        );
        FxPreviewResponse respIncoming = calculationService.previewConversion(reqIncoming);
        assertThat(respIncoming.scenarioVariance().impact()).isEqualTo("FAVORABLE"); // Incoming proceeds higher
    }

    @Test
    @DisplayName("Reciprocal rate and Quote-to-Base conversion")
    void testQuoteToBaseConversion() {
        // Principal 27,850.00 PKR converted back to USD @ rate 278.50
        FxPreviewRequest request = new FxPreviewRequest(
                "USD",
                "PKR",
                new BigDecimal("278.50"),
                FxDirection.OUTGOING,
                new BigDecimal("27850.00"),
                "PKR", // Principal is in Quote currency
                BigDecimal.ZERO,
                "USD",
                BigDecimal.ZERO,
                null,
                RateSourceType.MANUAL_USER_RATE
        );

        FxPreviewResponse response = calculationService.previewConversion(request);

        assertThat(response.settlementCurrency()).isEqualTo("USD");
        assertThat(response.grossSettlementAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(response.reciprocalRate()).isNotNull();
    }

    @Test
    @DisplayName("Percentage fee calculated correctly on gross settlement amount")
    void testPercentageFee() {
        // 100 USD @ 278.50 = 27,850 PKR. 1.0% fee = 278.50 PKR.
        FxPreviewRequest request = new FxPreviewRequest(
                "USD",
                "PKR",
                new BigDecimal("278.50"),
                FxDirection.OUTGOING,
                new BigDecimal("100.00"),
                "USD",
                new BigDecimal("50.00"), // Fixed fee
                "PKR",
                new BigDecimal("1.0"),   // 1% percentage fee
                null,
                RateSourceType.MANUAL_USER_RATE
        );

        FxPreviewResponse response = calculationService.previewConversion(request);

        assertThat(response.fixedFeeAmount()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(response.percentageFeeAmount()).isEqualByComparingTo(new BigDecimal("278.50"));
        assertThat(response.totalFeeAmount()).isEqualByComparingTo(new BigDecimal("328.50"));
        assertThat(response.totalSettlementDebited()).isEqualByComparingTo(new BigDecimal("28178.50"));
    }

    @Test
    @DisplayName("Validation fails when base and quote currencies are identical")
    void testIdenticalCurrenciesValidation() {
        FxPreviewRequest request = new FxPreviewRequest(
                "USD",
                "USD",
                new BigDecimal("1.0"),
                FxDirection.OUTGOING,
                new BigDecimal("100.00"),
                "USD",
                BigDecimal.ZERO,
                "USD",
                BigDecimal.ZERO,
                null,
                RateSourceType.MANUAL_USER_RATE
        );

        assertThatThrownBy(() -> calculationService.previewConversion(request))
                .isInstanceOf(FxValidationException.class)
                .hasMessageContaining("baseCurrency and quoteCurrency cannot be identical");
    }

    @Test
    @DisplayName("Validation fails when rate is non-positive")
    void testNonPositiveRateValidation() {
        FxPreviewRequest request = new FxPreviewRequest(
                "USD",
                "PKR",
                new BigDecimal("-5.0"),
                FxDirection.OUTGOING,
                new BigDecimal("100.00"),
                "USD",
                BigDecimal.ZERO,
                "PKR",
                BigDecimal.ZERO,
                null,
                RateSourceType.MANUAL_USER_RATE
        );

        assertThatThrownBy(() -> calculationService.previewConversion(request))
                .isInstanceOf(FxValidationException.class)
                .hasMessageContaining("rate must be strictly positive");
    }
}

