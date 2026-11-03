package com.example.postaxvatsystem.fixture;

import com.example.postaxvatsystem.dto.FiscalReceiptResult;
import com.example.postaxvatsystem.model.POSTransaction;
import com.example.postaxvatsystem.model.TaxSubmissionStatus;
import com.example.postaxvatsystem.service.FiscalAuthorityClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Component
@Profile("fixture")
public class FixtureFiscalAuthorityClient implements FiscalAuthorityClient {
    private final ObjectMapper objectMapper;
    private final Resource responseFixture;

    public FixtureFiscalAuthorityClient(
            ObjectMapper objectMapper,
            @Value("${fiscal.authority.fixture-file:classpath:fixtures/fiscal-authority-response.json}")
            Resource responseFixture) {
        this.objectMapper = objectMapper;
        this.responseFixture = responseFixture;
    }

    @Override
    public FiscalReceiptResult submitReceipt(POSTransaction transaction, boolean creditNote) {
        JsonNode fixture = readFixture();
        TaxSubmissionStatus status = parseStatus(fixture.path("submissionStatus").asText());
        String authorityTransactionId = prefixedValue(
                fixture.path("authorityTransactionIdPrefix").asText("TEST-TAX"),
                transaction, creditNote);
        String fiscalReference = null;
        String fiscalSignature = null;
        if (status == TaxSubmissionStatus.ACCEPTED) {
            String reference = fixture.path("fiscalReferencePrefix").asText("TEST-FISCAL");
            String signature = fixture.path("fiscalSignaturePrefix").asText("TEST-SIGNATURE");
            String uniqueValue = uniqueValue(transaction, creditNote);
            fiscalReference = reference + "-" + uniqueValue.substring(0, 12);
            fiscalSignature = signature + "-" + uniqueValue;
        }

        return new FiscalReceiptResult(
                fiscalReference,
                fiscalSignature,
                status,
                fixture.path("authorityResponseCode").asText("FIXTURE_RESPONSE"),
                fixture.path("authorityResponseMessage").asText("Simulated authority response; no authority was contacted."),
                status == TaxSubmissionStatus.QUEUED ? null : LocalDateTime.now(),
                authorityTransactionId,
                "FIXTURE"
        );
    }

    private JsonNode readFixture() {
        try (var input = responseFixture.getInputStream()) {
            JsonNode fixture = objectMapper.readTree(input);
            if (fixture == null || !fixture.isObject()) {
                throw new IllegalStateException("Fiscal authority response fixture must be a JSON object.");
            }
            return fixture;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to read fiscal authority response fixture: " + responseFixture, exception);
        }
    }

    private TaxSubmissionStatus parseStatus(String value) {
        try {
            return TaxSubmissionStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Unsupported submissionStatus in fiscal authority fixture: " + value, exception);
        }
    }

    private String prefixedValue(String prefix, POSTransaction transaction, boolean creditNote) {
        return prefix + "-" + uniqueValue(transaction, creditNote).substring(0, 12);
    }

    private String uniqueValue(POSTransaction transaction, boolean creditNote) {
        String identity = transaction.getTransactionId() + "|" + transaction.getGrossAmount()
                + "|" + transaction.getVatAmount() + "|" + transaction.getNetAmount()
                + "|" + creditNote;
        return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "").toUpperCase(Locale.ROOT);
    }
}
