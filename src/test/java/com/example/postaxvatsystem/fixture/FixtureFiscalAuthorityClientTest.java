package com.example.postaxvatsystem.fixture;

import com.example.postaxvatsystem.model.POSTransaction;
import com.example.postaxvatsystem.model.TaxSubmissionStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class FixtureFiscalAuthorityClientTest {

    @Test
    void createsClearlyMarkedReceiptIdentifiersForAcceptedFixtureResponse() {
        FixtureFiscalAuthorityClient client = client("""
                {
                  "submissionStatus": "ACCEPTED",
                  "authorityResponseCode": "FIXTURE_ACCEPTED",
                  "authorityResponseMessage": "Simulated acceptance",
                  "authorityTransactionIdPrefix": "TEST-TAX",
                  "fiscalReferencePrefix": "TEST-FISCAL",
                  "fiscalSignaturePrefix": "TEST-SIGNATURE"
                }
                """);

        var result = client.submitReceipt(transaction(), false);

        assertEquals(TaxSubmissionStatus.ACCEPTED, result.submissionStatus());
        assertEquals("FIXTURE", result.submissionMode());
        assertEquals("FIXTURE_ACCEPTED", result.authorityResponseCode());
        assertEquals("Simulated acceptance", result.authorityResponseMessage());
        assertNotNull(result.authorityTransactionId());
        assertNotNull(result.fiscalReference());
        assertNotNull(result.fiscalSignature());
        assertNotNull(result.submittedToAuthorityAt());
    }

    @Test
    void preservesRejectedAuthorityFixtureWithoutIssuingReceiptIdentifiers() {
        FixtureFiscalAuthorityClient client = client("""
                {
                  "submissionStatus": "REJECTED",
                  "authorityResponseCode": "TEST_INVALID_TAX_CATEGORY",
                  "authorityResponseMessage": "The fixture simulates an invalid tax category."
                }
                """);

        var result = client.submitReceipt(transaction(), true);

        assertEquals(TaxSubmissionStatus.REJECTED, result.submissionStatus());
        assertEquals("TEST_INVALID_TAX_CATEGORY", result.authorityResponseCode());
        assertNull(result.fiscalReference());
        assertNull(result.fiscalSignature());
        assertNotNull(result.authorityTransactionId());
    }

    @Test
    void leavesSubmissionTimeEmptyForQueuedFixtureResponse() {
        FixtureFiscalAuthorityClient client = client("""
                { "submissionStatus": "QUEUED" }
                """);

        var result = client.submitReceipt(transaction(), false);

        assertEquals(TaxSubmissionStatus.QUEUED, result.submissionStatus());
        assertNull(result.submittedToAuthorityAt());
        assertNull(result.fiscalReference());
        assertNull(result.fiscalSignature());
    }

    private FixtureFiscalAuthorityClient client(String fixture) {
        return new FixtureFiscalAuthorityClient(
                new ObjectMapper(),
                new ByteArrayResource(fixture.getBytes(StandardCharsets.UTF_8)));
    }

    private POSTransaction transaction() {
        POSTransaction transaction = new POSTransaction();
        transaction.setTransactionId("TXN-TEST-001");
        transaction.setTransactionType(com.example.postaxvatsystem.model.TransactionType.SALE);
        transaction.setGrossAmount(new BigDecimal("100.00"));
        transaction.setVatAmount(new BigDecimal("15.00"));
        transaction.setServiceFeeAmount(new BigDecimal("2.00"));
        transaction.setNetAmount(new BigDecimal("117.00"));
        return transaction;
    }
}
