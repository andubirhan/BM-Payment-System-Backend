package com.example.postaxvatsystem.fixture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.postaxvatsystem.service.BankTransactionIdGenerator;
import com.example.postaxvatsystem.testtransactiondata.TestTransactionDataService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixturePaymentDataProviderTest {

    @TempDir
    Path tempDir;

    @Test
    void replacesUnresolvedBankTransactionTemplateWithGeneratedReference() throws IOException {
        FixturePaymentDataProvider provider = providerWithBankResponse(
                "{\"body\":{\"transactionId\":\"{{$randomUUID}}\",\"status\":\"SETTLED\"}}");

        String response = provider.bankResponse();
        String transactionId = new ObjectMapper().readTree(response).path("transactionId").asText();

        assertTrue(transactionId.matches("BANK-TXN-\\d{8}-\\d{6}-\\d+"));
        assertFalse(transactionId.contains("{{"));
    }

    @Test
    void preservesBankTransactionReferenceWhenFixtureProvidesOne() throws IOException {
        FixturePaymentDataProvider provider = providerWithBankResponse(
                "{\"body\":{\"transactionId\":\"BANK-REFERENCE-123\",\"status\":\"SETTLED\"}}");

        String response = provider.bankResponse();

        assertEquals("BANK-REFERENCE-123", new ObjectMapper().readTree(response).path("transactionId").asText());
    }

    @Test
    void readsSuccessfulStatusFromConfiguredBankResponseFixture() throws IOException {
        FixturePaymentDataProvider provider = providerWithBankResponse(
                "{\"body\":{\"transactionId\":\"BANK-REFERENCE-123\",\"status\":\"COMPLETED\"}}");

        assertEquals("COMPLETED", provider.bankStatus().getStatus());
    }

    @Test
    void keepsUnknownBankResponseStatusIndeterminate() throws IOException {
        FixturePaymentDataProvider provider = providerWithBankResponse(
                "{\"body\":{\"transactionId\":\"BANK-REFERENCE-123\",\"status\":\"UNKNOWN\"}}");

        assertEquals("UNKNOWN", provider.bankStatus().getStatus());
    }

    @Test
    void readsBankResponseStatusFromTopLevelWhenBodyOmitsIt() throws IOException {
        FixturePaymentDataProvider provider = providerWithBankResponse(
                "{\"status\":\"REJECTED\",\"body\":{\"transactionId\":\"BANK-REFERENCE-123\"}}");

        assertEquals("REJECTED", provider.bankStatus().getStatus());
    }

    @Test
    void loadsTheAuthenticatedUsersUploadedPurchaseFixture() throws Exception {
        Path defaultPurchaseFile = tempDir.resolve("purchase.json");
        Path defaultSaleFile = tempDir.resolve("sale.json");
        Files.writeString(defaultPurchaseFile, "{\"befaPaymentId\":\"DEFAULT-ID\"}");
        Files.writeString(defaultSaleFile, "{\"befaPaymentId\":\"DEFAULT-SALE-ID\"}");
        TestTransactionDataService transactionData = new TestTransactionDataService(
                new ObjectMapper(),
                defaultPurchaseFile.toString(),
                defaultSaleFile.toString(),
                tempDir.resolve("uploads").toString());
        transactionData.save("PURCHASE", "cashier", new ObjectMapper().readTree(
                "{\"befaPaymentId\":\"CASHIER-ID\",\"lineItems\":[],\"paymentTenders\":[]}"));
        FixturePaymentDataProvider provider = new FixturePaymentDataProvider(
                new ObjectMapper(),
                transactionData,
                tempDir.resolve("bank-response.json").toString(),
                new BankTransactionIdGenerator());

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "cashier", "ignored", List.of(new SimpleGrantedAuthority("ROLE_CASHIER"))));
        try {
            assertEquals("CASHIER-ID", provider.preview("cashier-id", com.example.postaxvatsystem.model.TransactionType.PURCHASE)
                    .get("befaPaymentId"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private FixturePaymentDataProvider providerWithBankResponse(String fixtureContent) throws IOException {
        Path bankResponseFile = tempDir.resolve("bank-response.json");
        Files.writeString(bankResponseFile, fixtureContent);
        return new FixturePaymentDataProvider(
                new ObjectMapper(),
                new TestTransactionDataService(
                        new ObjectMapper(),
                        tempDir.resolve("purchase.json").toString(),
                        tempDir.resolve("sale.json").toString(),
                        tempDir.resolve("uploads").toString()),
                bankResponseFile.toString(),
                new BankTransactionIdGenerator());
    }
}
