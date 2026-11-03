package com.example.postaxvatsystem.client;

import com.example.postaxvatsystem.dto.BankStatusResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
/**
 * Downstream wrapper communicating directly with the external bank processing engine.
 * Implements idempotent network requests with X-Idempotency-Key headers.
 */
@Component
@Slf4j
public class BankApiClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String BANK_API_URL = "https://internal-bank.com";

    /**
     * Sends payment request to bank with idempotency key to prevent double charges.
     * 
     * @param idempotencyKey Unique key for idempotency
     * @param amount Amount in major currency units
     * @param details Payment description
     * @return Bank response as string
     */
    public String sendPayment(String idempotencyKey, BigDecimal amount, String currencyCode, String details) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Idempotency-Key", idempotencyKey);

        String body = String.format("{\"amount\": %s, \"currency\": \"%s\", \"description\": \"%s\"}",
                amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(), currencyCode, details);
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        log.info("Sending bank payment request with idempotency key: {}", idempotencyKey);
        
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(BANK_API_URL, entity, String.class);
            log.info("Bank payment response status: {}", response.getStatusCode());
            return response.getBody();
        } catch (ResourceAccessException e) {
            log.error("Bank API unreachable: {}", e.getMessage());
            throw new RuntimeException("Bank API is currently unreachable. Please try again later.", e);
        } catch (Exception e) {
            log.error("Error sending bank payment: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Checks the status of a payment using the idempotency key.
     * Used by the reconciliation scheduler to determine if a stranded transaction was actually processed.
     * 
     * @param idempotencyKey Unique key to look up
     * @return BankStatusResponse with status and raw response
     */
    public BankStatusResponse checkStatus(String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Idempotency-Key", idempotencyKey);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        log.info("Checking bank payment status for idempotency key: {}", idempotencyKey);

        try {
            ResponseEntity<BankStatusResponse> response = restTemplate.exchange(
                    BANK_API_URL + "/status",
                    HttpMethod.GET,
                    entity,
                    BankStatusResponse.class
            );
            
            BankStatusResponse statusResponse = response.getBody();
            log.info("Bank status check result: {}", statusResponse != null ? statusResponse.getStatus() : "null");
            return statusResponse;
        } catch (ResourceAccessException e) {
            log.warn("Bank API unreachable during status check: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("Bank status lookup unavailable: {}", e.getMessage());
            return null;
        }
    }
}
