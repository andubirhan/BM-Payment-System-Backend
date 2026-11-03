package com.example.postaxvatsystem.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class EncryptionService {

    @Value("${encryption.key}")
    private String encryptionKey;

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String CIPHERTEXT_PREFIX = "v1:";
    private static final int TAG_LENGTH_BIT = 128;
    private static final int TAG_LENGTH_BYTE = TAG_LENGTH_BIT / 8;
    private static final int IV_LENGTH_BYTE = 12;
    private static final int MIN_CIPHERTEXT_LENGTH = IV_LENGTH_BYTE + TAG_LENGTH_BYTE;
    private static final int AES_KEY_BIT = 256;
    private static final int ITERATIONS = 100000;
    private static final byte[] SALT = "POS_TAX_VAT_SALT".getBytes(StandardCharsets.UTF_8);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final AtomicBoolean LEGACY_DECRYPTION_WARNING_LOGGED = new AtomicBoolean();

    private volatile SecretKeySpec cachedKey;
    private volatile SecretKeySpec cachedLegacyKey;

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH_BYTE];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(), new GCMParameterSpec(TAG_LENGTH_BIT, iv));

            byte[] encryptedBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Combine IV and encrypted data
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + encryptedBytes.length);
            byteBuffer.put(iv);
            byteBuffer.put(encryptedBytes);

            return CIPHERTEXT_PREFIX + Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isEmpty()) {
            return ciphertext;
        }

        boolean versioned = ciphertext.startsWith(CIPHERTEXT_PREFIX);
        String encodedPayload = versioned
                ? ciphertext.substring(CIPHERTEXT_PREFIX.length())
                : ciphertext;
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encodedPayload);
        } catch (IllegalArgumentException e) {
            if (versioned) {
                throw new IllegalStateException("Versioned encrypted data is not valid Base64.", e);
            }
            return ciphertext;
        }
        if (decoded.length < MIN_CIPHERTEXT_LENGTH) {
            if (versioned) {
                throw new IllegalStateException("Versioned encrypted data is shorter than its IV and authentication tag.");
            }
            return ciphertext;
        }

        try {
            return decryptPayload(decoded, getSecretKey());
        } catch (Exception currentKeyFailure) {
            if (!versioned) {
                try {
                    return decryptPayload(decoded, getLegacySecretKey());
                } catch (Exception legacyKeyFailure) {
                    if (LEGACY_DECRYPTION_WARNING_LOGGED.compareAndSet(false, true)) {
                        log.warn("Unversioned encrypted data could not be decrypted with current or legacy key. "
                                + "Check the configured encryption key and run the encryption migration.");
                    }
                    return ciphertext;
                }
            }
            throw new IllegalStateException("Unable to decrypt versioned encrypted data.", currentKeyFailure);
        }
    }

    /**
     * Checks if data can be decrypted successfully
     * Returns true if decryption succeeds, false otherwise
     */
    public boolean canDecrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isEmpty()) {
            return false;
        }

        String encodedPayload = ciphertext.startsWith(CIPHERTEXT_PREFIX)
                ? ciphertext.substring(CIPHERTEXT_PREFIX.length())
                : ciphertext;
        boolean versioned = ciphertext.startsWith(CIPHERTEXT_PREFIX);
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encodedPayload);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (decoded.length < MIN_CIPHERTEXT_LENGTH) {
            return false;
        }

        try {
            decryptPayload(decoded, getSecretKey());
            return true;
        } catch (Exception e) {
            if (versioned) return false;
            try {
                decryptPayload(decoded, getLegacySecretKey());
                return true;
            } catch (Exception legacyFailure) {
                return false;
            }
        }
    }

    public boolean isCurrentFormat(String ciphertext) {
        return ciphertext != null && ciphertext.startsWith(CIPHERTEXT_PREFIX);
    }

    public boolean hasEncryptedPayloadShape(String value) {
        if (value == null || value.isEmpty()) return false;
        String encodedPayload = value.startsWith(CIPHERTEXT_PREFIX)
                ? value.substring(CIPHERTEXT_PREFIX.length())
                : value;
        try {
            return Base64.getDecoder().decode(encodedPayload).length >= MIN_CIPHERTEXT_LENGTH;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String decryptPayload(byte[] decoded, SecretKeySpec key) throws Exception {
        ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
        byte[] iv = new byte[IV_LENGTH_BYTE];
        byteBuffer.get(iv);
        byte[] encryptedBytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(encryptedBytes);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BIT, iv));
        return new String(cipher.doFinal(encryptedBytes), StandardCharsets.UTF_8);
    }

    /**
     * Derives a 256-bit key using PBKDF2WithHmacSHA256
     * Per PCI DSS Requirement 3.5.2
     */
    private byte[] deriveKey(String password) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM);
        char[] passwordChars = password.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(passwordChars, SALT, ITERATIONS, AES_KEY_BIT);
        try {
            SecretKey tmp = factory.generateSecret(spec);
            return tmp.getEncoded();
        } finally {
            spec.clearPassword();
            Arrays.fill(passwordChars, '\0');
        }
    }

    private SecretKeySpec getSecretKey() throws Exception {
        SecretKeySpec key = cachedKey;
        if (key == null) {
            synchronized (this) {
                key = cachedKey;
                if (key == null) {
                    key = new SecretKeySpec(deriveKey(encryptionKey), ALGORITHM);
                    cachedKey = key;
                }
            }
        }
        return key;
    }

    private SecretKeySpec getLegacySecretKey() {
        SecretKeySpec key = cachedLegacyKey;
        if (key == null) {
            synchronized (this) {
                key = cachedLegacyKey;
                if (key == null) {
                    byte[] passwordBytes = encryptionKey.getBytes(StandardCharsets.UTF_8);
                    byte[] keyBytes = new byte[AES_KEY_BIT / 8];
                    try {
                        System.arraycopy(passwordBytes, 0, keyBytes, 0,
                                Math.min(passwordBytes.length, keyBytes.length));
                        key = new SecretKeySpec(keyBytes, ALGORITHM);
                        cachedLegacyKey = key;
                    } finally {
                        Arrays.fill(passwordBytes, (byte) 0);
                        Arrays.fill(keyBytes, (byte) 0);
                    }
                }
            }
        }
        return key;
    }
}
