package com.example.postaxvatsystem.fixture;

import com.example.postaxvatsystem.dto.BefaPaymentDataDTO;
import com.example.postaxvatsystem.dto.BankStatusResponse;
import com.example.postaxvatsystem.exception.ResourceNotFoundException;
import com.example.postaxvatsystem.model.TransactionType;
import com.example.postaxvatsystem.service.BankTransactionIdGenerator;
import com.example.postaxvatsystem.testtransactiondata.TestTransactionDataService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Development-only third-party simulator backed by per-user BEFA fixture uploads. */
@Component
@Profile("fixture")
public class FixturePaymentDataProvider {
    private final ObjectMapper objectMapper;
    private final TestTransactionDataService testTransactionDataService;
    private final Path bankResponseFile;
    private final BankTransactionIdGenerator bankTransactionIdGenerator;

    public FixturePaymentDataProvider(
            ObjectMapper objectMapper,
            TestTransactionDataService testTransactionDataService,
            @Value("${payment.fixture.bank-response-file}") String bankResponseFile,
            BankTransactionIdGenerator bankTransactionIdGenerator) {
        this.objectMapper = objectMapper;
        this.testTransactionDataService = testTransactionDataService;
        this.bankResponseFile = Path.of(bankResponseFile);
        this.bankTransactionIdGenerator = bankTransactionIdGenerator;
    }

    public BefaPaymentDataDTO findBefaPayment(String befaPaymentId, TransactionType transactionType) {
        JsonNode root = loadTransactionFixture(transactionType);
        String fixtureId = root.path("befaPaymentId").asText();
        if (!befaPaymentId.equalsIgnoreCase(fixtureId)) {
            throw new ResourceNotFoundException("BEFA Payment", befaPaymentId);
        }

        // Calculate total amount from payment tenders
        BigDecimal tendered = BigDecimal.ZERO;
        for (JsonNode tender : root.path("paymentTenders")) {
            tendered = tendered.add(tender.path("amountTendered").decimalValue());
        }
        if (tendered.signum() == 0) {
            for (JsonNode item : root.path("lineItems")) {
                tendered = tendered.add(item.path("unitPrice").decimalValue()
                        .multiply(BigDecimal.valueOf(item.path("quantity").asLong())));
            }
        }

        // Map line items
        java.util.List<BefaPaymentDataDTO.LineItem> lineItems = new java.util.ArrayList<>();
        for (JsonNode item : root.path("lineItems")) {
            lineItems.add(new BefaPaymentDataDTO.LineItem(
                item.has("itemCode") && !item.path("itemCode").isNull() ? item.path("itemCode").asText() : "",
                item.has("itemName") && !item.path("itemName").isNull() ? item.path("itemName").asText() : "",
                item.has("unitPrice") && !item.path("unitPrice").isNull() ? item.path("unitPrice").decimalValue() : BigDecimal.ZERO,
                item.has("quantity") && !item.path("quantity").isNull() ? item.path("quantity").asLong() : 0L,
                item.has("discountAmount") && !item.path("discountAmount").isNull() ? item.path("discountAmount").decimalValue() : BigDecimal.ZERO,
                item.has("vatExempt") && !item.path("vatExempt").isNull() ? item.path("vatExempt").asBoolean() : false,
                item.has("vatCategory") && !item.path("vatCategory").isNull() ? item.path("vatCategory").asText() : ""
            ));
        }

        // Map payment tenders
        java.util.List<BefaPaymentDataDTO.PaymentTender> paymentTenders = new java.util.ArrayList<>();
        for (JsonNode tender : root.path("paymentTenders")) {
            paymentTenders.add(new BefaPaymentDataDTO.PaymentTender(
                tender.has("paymentMethod") && !tender.path("paymentMethod").isNull() ? tender.path("paymentMethod").asText() : "",
                tender.has("paymentProvider") && !tender.path("paymentProvider").isNull() ? tender.path("paymentProvider").asText() : "",
                tender.has("amountTendered") && !tender.path("amountTendered").isNull() ? tender.path("amountTendered").decimalValue() : BigDecimal.ZERO
            ));
        }

        // Map headers
        java.util.Map<String, String> headers = new java.util.HashMap<>();
        JsonNode headersNode = root.path("headers");
        if (headersNode.isObject()) {
            headersNode.fields().forEachRemaining(entry ->
                headers.put(entry.getKey(), entry.getValue() != null && !entry.getValue().isNull() ? entry.getValue().asText() : "")
            );
        }

        BefaPaymentDataDTO dto = new BefaPaymentDataDTO();
        dto.setBefaPaymentId(fixtureId);
        dto.setIdempotencyKey(root.has("idempotencyKey") && !root.path("idempotencyKey").isNull() ? root.path("idempotencyKey").asText() : "");
        dto.setCashRegisterId(root.has("cashRegisterId") && !root.path("cashRegisterId").isNull() ? root.path("cashRegisterId").asText() : "");
        dto.setCashierId(root.has("cashierId") && !root.path("cashierId").isNull() ? root.path("cashierId").asText() : "");
        dto.setCustomerTin(root.has("customerTin") && !root.path("customerTin").isNull() ? root.path("customerTin").asText() : "");
        dto.setCustomerName(root.has("customerName") && !root.path("customerName").isNull() ? root.path("customerName").asText() : "");
        dto.setAmountInCents(tendered);
        dto.setLineItems(lineItems);
        dto.setPaymentTenders(paymentTenders);
        dto.setHeaders(headers);

        return dto;
    }

    public java.util.Map<String, Object> preview(String befaPaymentId, TransactionType transactionType) {
        JsonNode root = loadTransactionFixture(transactionType);
        if (!befaPaymentId.equalsIgnoreCase(root.path("befaPaymentId").asText())) {
            throw new ResourceNotFoundException("BEFA Payment", befaPaymentId);
        }
        return objectMapper.convertValue(root, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
    }
    public String bankResponse() {
        JsonNode root = read(bankResponseFile);
        JsonNode bodyNode = responseBody(root);
        if (!bodyNode.isObject()) {
            throw new IllegalStateException("Bank payment fixture must contain a JSON body object: " + bankResponseFile);
        }

        ObjectNode body = (ObjectNode) bodyNode;
        String transactionId = body.path("bankTransactionId").asText();
        if (transactionId.isBlank()) {
            transactionId = body.path("transactionId").asText();
        }
        if (!isUsableBankTransactionId(transactionId)) {
            body.put("transactionId", bankTransactionIdGenerator.nextId());
        }
        return body.toString();
    }

    public BankStatusResponse bankStatus() {
        BankStatusResponse result = new BankStatusResponse();
        String rawResponse = bankResponse();
        JsonNode response = read(bankResponseFile);
        JsonNode body = responseBody(response);
        String status = body.path("status").asText();
        if (status.isBlank()) status = response.path("status").asText();
        result.setStatus(status.isBlank() ? "UNKNOWN" : status.trim().toUpperCase(Locale.ROOT));
        result.setRawResponseJson(rawResponse);
        return result;
    }

    private JsonNode responseBody(JsonNode response) {
        JsonNode body = response.path("body");
        return body.isObject() ? body : response;
    }

    private JsonNode loadTransactionFixture(TransactionType transactionType) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                ? null : authentication.getName();
        return testTransactionDataService.load(transactionType.name(), username);
    }

    private boolean isUsableBankTransactionId(String bankTransactionId) {
        return bankTransactionId != null
                && !bankTransactionId.isBlank()
                && !bankTransactionId.contains("{{");
    }

    private JsonNode read(Path file) {
        try {
            return objectMapper.readTree(Files.readString(file));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read payment fixture: " + file, exception);
        }
    }
}