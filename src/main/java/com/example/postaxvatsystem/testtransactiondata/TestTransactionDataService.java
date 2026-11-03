package com.example.postaxvatsystem.testtransactiondata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

@Service
@Profile("fixture")
public class TestTransactionDataService {

    private final ObjectMapper objectMapper;
    private final Path purchaseFile;
    private final Path saleFile;
    private final Path uploadDirectory;

    public TestTransactionDataService(
            ObjectMapper objectMapper,
            @Value("${payment.fixture.befa-file}") String purchaseFile,
            @Value("${payment.fixture.sale-befa-file}") String saleFile,
            @Value("${payment.fixture.user-upload-dir:./uploads/test-transaction-data}") String uploadDirectory) {
        this.objectMapper = objectMapper;
        this.purchaseFile = Path.of(purchaseFile).toAbsolutePath().normalize();
        this.saleFile = Path.of(saleFile).toAbsolutePath().normalize();
        this.uploadDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize();
        if (this.purchaseFile.equals(this.saleFile)) {
            throw new IllegalArgumentException("Purchase and sale fixtures must use separate files.");
        }
    }

    public JsonNode load(String transactionType, String username) {
        Path defaultFile = resolveFile(transactionType);
        Path uploadedFile = username == null || username.isBlank()
                ? null : resolveUploadedFile(transactionType, username);
        Path file = uploadedFile != null && Files.isRegularFile(uploadedFile) ? uploadedFile : defaultFile;
        if (!Files.isRegularFile(file)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "The configured fixture file was not found.");
        }
        try {
            JsonNode data = objectMapper.readTree(file.toFile());
            validate(data);
            return data;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read the configured transaction fixture.", exception);
        }
    }

    public JsonNode save(String transactionType, String username, JsonNode data) {
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in before saving a test fixture.");
        }
        Path target = resolveUploadedFile(transactionType, username);
        validate(data);
        Path temporaryFile = null;
        try {
            Path parent = target.getParent();
            Files.createDirectories(parent);
            temporaryFile = Files.createTempFile(parent, target.getFileName().toString(), ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporaryFile.toFile(), data);
            try {
                Files.move(temporaryFile, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryFile, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return data;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to save the uploaded transaction fixture.", exception);
        } finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException exception) {
                    throw new IllegalStateException("Unable to clean up the temporary uploaded fixture file.", exception);
                }
            }
        }
    }

    private Path resolveUploadedFile(String transactionType, String username) {
        String normalizedType = normalizeType(transactionType);
        Path userDirectory = uploadDirectory.resolve(userDirectoryName(username)).normalize();
        if (!userDirectory.startsWith(uploadDirectory)) {
            throw new IllegalStateException("The test fixture upload path is invalid.");
        }
        return userDirectory.resolve(normalizedType.toLowerCase(Locale.ROOT) + ".json");
    }

    private String userDirectoryName(String username) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(username.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to create a private test fixture path.", exception);
        }
    }

    private String normalizeType(String transactionType) {
        String normalizedType = transactionType == null
                ? "" : transactionType.trim().toUpperCase(Locale.ROOT);
        if (!normalizedType.equals("PURCHASE") && !normalizedType.equals("SALE")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Transaction type must be PURCHASE or SALE.");
        }
        return normalizedType;
    }

    private Path resolveFile(String transactionType) {
        return switch (normalizeType(transactionType)) {
            case "PURCHASE" -> purchaseFile;
            case "SALE" -> saleFile;
            default -> throw new IllegalStateException("Unsupported transaction type.");
        };
    }

    private void validate(JsonNode data) {
        if (data == null || !data.isObject()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transaction data must be a JSON object.");
        }
        if (!data.path("befaPaymentId").isTextual()
                || data.path("befaPaymentId").asText().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BEFA payment ID is required.");
        }
        if (data.has("lineItems") && !data.path("lineItems").isArray()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Line items must be a JSON array.");
        }
        if (data.has("paymentTenders") && !data.path("paymentTenders").isArray()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment tenders must be a JSON array.");
        }
        if (data.has("headers") && !data.path("headers").isObject()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Headers must be a JSON object.");
        }
    }
}
