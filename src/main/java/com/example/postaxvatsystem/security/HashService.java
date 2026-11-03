package com.example.postaxvatsystem.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Service
@Slf4j
public class HashService {

    @Value("${hash.key}")
    private String hashKey;

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int AES_KEY_BIT = 256;
    private static final int ITERATIONS = 100000;
    private static final byte[] SALT = "POS_TAX_VAT_HASH_SALT".getBytes(StandardCharsets.UTF_8);

    /**
     * Creates a deterministic hash for searchable fields.
     * Uses HMAC-SHA256 to ensure the same plaintext always produces the same hash.
     * Normalizes to lowercase for case-insensitive searching.
     *
     * @param plaintext The text to hash
     * @return Base64-encoded HMAC hash
     */
    public String hash(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return null;
        }

        try {
            byte[] keyBytes = deriveKey(hashKey);

            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, HMAC_ALGORITHM);
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(secretKey);

            // Normalize to lowercase for case-insensitive search
            byte[] hashBytes = mac.doFinal(plaintext.toLowerCase().getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (Exception e) {
            log.error("Hashing failed", e);
            throw new RuntimeException("Hashing failed", e);
        }
    }

    /**
     * Derives a 256-bit key using PBKDF2WithHmacSHA256
     * Per PCI DSS Requirement 3.5.2
     */
    private byte[] deriveKey(String password) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM);
        PBEKeySpec spec = new PBEKeySpec(
            password.toCharArray(),
            SALT,
            ITERATIONS,
            AES_KEY_BIT
        );
        SecretKey tmp = factory.generateSecret(spec);
        return tmp.getEncoded();
    }
}
