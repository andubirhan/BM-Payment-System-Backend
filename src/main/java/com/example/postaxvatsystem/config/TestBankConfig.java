package com.example.postaxvatsystem.config;

import com.example.postaxvatsystem.client.BankApiClient;
import com.example.postaxvatsystem.dto.BankStatusResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.math.BigDecimal;

/**
 * Test configuration that provides a mock BankApiClient for reconciliation testing.
 * 
 * This configuration is only active when:
 * 1. The "test" profile is active
 * 2. The property "reconciliation.bank.mock.enabled" is set to "true"
 * 
 * The mock BankApiClient always returns SUCCESS status, allowing testing of the
 * PaymentReconciliationScheduler without needing a real bank API connection.
 */
@Configuration
@Profile("test")
public class TestBankConfig {

    @Bean
    @Primary
    @ConditionalOnProperty(name = "reconciliation.bank.mock.enabled", havingValue = "true")
    public BankApiClient mockBankApiClient() {
        return new BankApiClient() {
            @Override
            public BankStatusResponse checkStatus(String idempotencyKey) {
                BankStatusResponse response = new BankStatusResponse();
                response.setStatus("SUCCESS");
                response.setRawResponseJson("{\"status\":\"SUCCESS\",\"transactionId\":\"mock-" + idempotencyKey + "\"}");
                return response;
            }
            
            @Override
            public String sendPayment(String idempotencyKey, BigDecimal amount, String currencyCode, String details) {
                return "{\"status\":\"SUCCESS\",\"transactionId\":\"mock-" + idempotencyKey + "\"}";
            }
        };
    }
}
