package com.example.postaxvatsystem.testtransactiondata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestTransactionDataServiceTest {

    @TempDir
    Path tempDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void savesPurchaseFixtureToPrivateUserUploadWithoutChangingDefaultFixture() throws Exception {
        TestTransactionDataService service = service();
        Path purchaseFixture = tempDirectory.resolve("fixtures").resolve("befa-payment.json");
        Files.createDirectories(purchaseFixture.getParent());
        Files.writeString(purchaseFixture, "{\"befaPaymentId\":\"OLD-ID\"}");
        JsonNode data = objectMapper.readTree("""
                {"befaPaymentId":"BEFA-TEST-1","lineItems":[],"paymentTenders":[]}
                """);

        service.save("PURCHASE", "cashier", data);

        assertEquals("OLD-ID", objectMapper.readTree(purchaseFixture.toFile()).path("befaPaymentId").asText());
        assertEquals(data, service.load("PURCHASE", "cashier"));
        assertEquals("OLD-ID", service.load("PURCHASE", "manager").path("befaPaymentId").asText());
        assertFalse(Files.exists(tempDirectory.resolve("fixtures").resolve("befa-payment-sale.json")));
    }

    @Test
    void savesSaleFixtureSeparatelyFromPurchaseFixtureAndOtherUsers() throws Exception {
        TestTransactionDataService service = service();
        Path purchaseFixture = tempDirectory.resolve("fixtures").resolve("befa-payment.json");
        Path saleFixture = tempDirectory.resolve("fixtures").resolve("befa-payment-sale.json");
        Files.createDirectories(purchaseFixture.getParent());
        Files.writeString(purchaseFixture, "{\"befaPaymentId\":\"PURCHASE-ID\"}");
        Files.writeString(saleFixture, "{\"befaPaymentId\":\"OLD-SALE-ID\"}");
        JsonNode data = objectMapper.readTree("""
                {"befaPaymentId":"BEFA-SALE-1","lineItems":[]}
                """);

        service.save("SALE", "cashier", data);

        assertEquals("OLD-SALE-ID", objectMapper.readTree(saleFixture.toFile()).path("befaPaymentId").asText());
        assertEquals(data, service.load("SALE", "cashier"));
        assertEquals("OLD-SALE-ID", service.load("SALE", "manager").path("befaPaymentId").asText());
        assertEquals("PURCHASE-ID", objectMapper.readTree(purchaseFixture.toFile()).path("befaPaymentId").asText());
    }

    @Test
    void rejectsInvalidTypesAndMissingBefaPaymentIds() throws Exception {
        TestTransactionDataService service = service();
        JsonNode noBefaId = objectMapper.readTree("{\"lineItems\":[]}");

        assertThrows(ResponseStatusException.class, () -> service.load("../other", "cashier"));
        assertThrows(ResponseStatusException.class, () -> service.save("PURCHASE", "cashier", noBefaId));
        assertThrows(ResponseStatusException.class, () -> service.save("PURCHASE", "", noBefaId));
    }

    @Test
    void readsTheCurrentlyConfiguredFixtureFile() throws Exception {
        TestTransactionDataService service = service();
        Path purchaseFixture = tempDirectory.resolve("fixtures").resolve("befa-payment.json");
        Files.createDirectories(purchaseFixture.getParent());
        Files.writeString(purchaseFixture, "{\"befaPaymentId\":\"BEFA-1\"}");

        assertEquals("BEFA-1", service.load("PURCHASE", "cashier").path("befaPaymentId").asText());
    }

    private TestTransactionDataService service() {
        Path fixtures = tempDirectory.resolve("fixtures");
        return new TestTransactionDataService(
                objectMapper,
                fixtures.resolve("befa-payment.json").toString(),
                fixtures.resolve("befa-payment-sale.json").toString(),
                tempDirectory.resolve("uploads").toString());
    }
}
