package com.app.sme_health_backend.fx.service;

import com.app.sme_health_backend.fx.exception.FxValidationException;
import com.app.sme_health_backend.fx.model.CurrencyMetadata;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Authoritative central registry for ISO 4217 currency metadata and precision rules.
 * Governs minor-unit scales (e.g. JPY=0, PKR/USD/EUR/GBP/AED/SAR=2, KWD/BHD/OMR=3).
 */
@Service
public class CurrencyMetadataService {

    private final Map<String, CurrencyMetadata> currencyRegistry;

    public CurrencyMetadataService() {
        Map<String, CurrencyMetadata> map = new LinkedHashMap<>();

        // Base & major trading currencies
        register(map, new CurrencyMetadata("PKR", "Pakistani Rupee", "₨", 2, RoundingMode.HALF_UP, true));
        register(map, new CurrencyMetadata("USD", "US Dollar", "$", 2, RoundingMode.HALF_UP, true));
        register(map, new CurrencyMetadata("EUR", "Euro", "€", 2, RoundingMode.HALF_UP, true));
        register(map, new CurrencyMetadata("GBP", "British Pound", "£", 2, RoundingMode.HALF_UP, true));
        register(map, new CurrencyMetadata("AED", "UAE Dirham", "AED", 2, RoundingMode.HALF_UP, true));
        register(map, new CurrencyMetadata("SAR", "Saudi Riyal", "SAR", 2, RoundingMode.HALF_UP, true));

        // 0-decimal minor-unit currency
        register(map, new CurrencyMetadata("JPY", "Japanese Yen", "¥", 0, RoundingMode.HALF_UP, true));

        // 3-decimal minor-unit currencies
        register(map, new CurrencyMetadata("KWD", "Kuwaiti Dinar", "KD", 3, RoundingMode.HALF_UP, true));
        register(map, new CurrencyMetadata("BHD", "Bahraini Dinar", "BD", 3, RoundingMode.HALF_UP, true));
        register(map, new CurrencyMetadata("OMR", "Omani Rial", "OMR", 3, RoundingMode.HALF_UP, true));

        this.currencyRegistry = Collections.unmodifiableMap(map);
    }

    private void register(Map<String, CurrencyMetadata> map, CurrencyMetadata metadata) {
        map.put(metadata.code().toUpperCase(Locale.ROOT), metadata);
    }

    public Optional<CurrencyMetadata> getCurrency(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(currencyRegistry.get(code.trim().toUpperCase(Locale.ROOT)));
    }

    public CurrencyMetadata requireCurrency(String code) {
        return getCurrency(code).orElseThrow(() ->
                new FxValidationException("Unsupported currency: '" + code + "'. Supported currencies: " + currencyRegistry.keySet())
        );
    }

    public List<CurrencyMetadata> getAllCurrencies() {
        return new ArrayList<>(currencyRegistry.values());
    }

    public boolean isSupported(String code) {
        return getCurrency(code).isPresent();
    }

    public BigDecimal round(String currencyCode, BigDecimal amount) {
        CurrencyMetadata meta = requireCurrency(currencyCode);
        return meta.round(amount);
    }
}

