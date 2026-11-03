package com.example.postaxvatsystem.controller;

import com.example.postaxvatsystem.dto.CurrencyConfigurationDTO;
import com.example.postaxvatsystem.dto.PricingConfigurationDTO;
import com.example.postaxvatsystem.service.PricingConfigurationService;
import com.example.postaxvatsystem.service.POSConfigurationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/pos/settings")
@RequiredArgsConstructor
public class POSConfigurationController {

    private final POSConfigurationService configurationService;
    private final PricingConfigurationService pricingConfigurationService;

    @GetMapping("/currency")
    public ResponseEntity<Map<String, String>> getCurrency() {
        return ResponseEntity.ok(Map.of("currencyCode", configurationService.getCurrencyCode()));
    }

    @PutMapping("/currency")
    public ResponseEntity<Map<String, String>> updateCurrency(
            @Valid @RequestBody CurrencyConfigurationDTO request) {
        return ResponseEntity.ok(Map.of(
                "currencyCode",
                configurationService.updateCurrencyCode(request.getCurrencyCode())
        ));
    }

    @GetMapping("/pricing")
    public ResponseEntity<PricingConfigurationDTO> getPricingConfiguration() {
        return ResponseEntity.ok(pricingConfigurationService.getConfiguration());
    }

    @PutMapping("/pricing")
    public ResponseEntity<PricingConfigurationDTO> updatePricingConfiguration(
            @Valid @RequestBody PricingConfigurationDTO request) {
        return ResponseEntity.ok(pricingConfigurationService.updateConfiguration(request));
    }
}
