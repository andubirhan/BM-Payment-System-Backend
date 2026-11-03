package com.example.postaxvatsystem.service;

import com.example.postaxvatsystem.model.POSConfiguration;
import com.example.postaxvatsystem.repository.POSConfigurationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Currency;
import java.util.Locale;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class POSConfigurationService {

    private static final String CURRENCY_KEY = "currency_code";
    private final POSConfigurationRepository repository;

    @Value("${pos.currency.code:ETB}")
    private String defaultCurrencyCode;

    @Value("${vat.service.fee.rate:0.02}")
    private BigDecimal defaultServiceFeeRate;

    @Transactional
    public String getCurrencyCode() {
        return repository.findById(CURRENCY_KEY)
                .map(POSConfiguration::getValue)
                .orElseGet(() -> {
                    POSConfiguration config = new POSConfiguration();
                    config.setKey(CURRENCY_KEY);
                    config.setValue(normalize(defaultCurrencyCode));
                    return repository.save(config).getValue();
                });
    }

    @Transactional
    public String updateCurrencyCode(String currencyCode) {
        String normalized = normalize(currencyCode);
        try {
            Currency.getInstance(normalized);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Currency code is not a supported ISO 4217 code", error);
        }
        POSConfiguration config = repository.findById(CURRENCY_KEY).orElseGet(POSConfiguration::new);
        config.setKey(CURRENCY_KEY);
        config.setValue(normalized);
        return repository.save(config).getValue();
    }

    @Transactional
    public BigDecimal getServiceFeeRate() {
        return repository.findById("service_fee_rate")
                .map(config -> new BigDecimal(config.getValue()))
                .orElseGet(() -> updateServiceFeeRate(defaultServiceFeeRate));
    }

    @Transactional
    public BigDecimal updateServiceFeeRate(BigDecimal rate) {
        if (rate == null || rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Service fee rate must be between 0 and 1");
        }
        POSConfiguration config = repository.findById("service_fee_rate").orElseGet(POSConfiguration::new);
        config.setKey("service_fee_rate");
        config.setValue(rate.setScale(6, RoundingMode.HALF_UP).toPlainString());
        repository.save(config);
        return new BigDecimal(config.getValue());
    }

    @Transactional
    public BigDecimal getSaleWithholdingVatRate() {
        return getRate("sale_withholding_vat_rate");
    }

    @Transactional
    public BigDecimal getPurchaseWithholdingVatRate() {
        return getRate("purchase_withholding_vat_rate");
    }

    @Transactional
    public BigDecimal updateSaleWithholdingVatRate(BigDecimal rate) {
        return updateRate("sale_withholding_vat_rate", rate);
    }

    @Transactional
    public BigDecimal updatePurchaseWithholdingVatRate(BigDecimal rate) {
        return updateRate("purchase_withholding_vat_rate", rate);
    }

    private BigDecimal getRate(String key) {
        return repository.findById(key)
                .map(config -> new BigDecimal(config.getValue()))
                .orElseGet(() -> updateRate(key, BigDecimal.ZERO));
    }

    private BigDecimal updateRate(String key, BigDecimal rate) {
        if (rate == null || rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Withholding VAT rate must be between 0 and 1");
        }
        POSConfiguration config = repository.findById(key).orElseGet(POSConfiguration::new);
        config.setKey(key);
        config.setValue(rate.setScale(6, RoundingMode.HALF_UP).toPlainString());
        repository.save(config);
        return new BigDecimal(config.getValue());
    }

    private String normalize(String currencyCode) {
        if (currencyCode == null || !currencyCode.trim().matches("[A-Za-z]{3}")) {
            throw new IllegalArgumentException("Currency code must be a three-letter ISO code");
        }
        return currencyCode.trim().toUpperCase(Locale.ROOT);
    }
}
