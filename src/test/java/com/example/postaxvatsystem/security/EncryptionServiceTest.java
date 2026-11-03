package com.example.postaxvatsystem.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncryptionServiceTest {

    @Test
    void encryptsAndDecryptsValuesUsingCachedKey() {
        EncryptionService service = new EncryptionService();
        ReflectionTestUtils.setField(service, "encryptionKey", "test-encryption-key-for-unit-tests");

        String plaintext = "customer-sensitive-value";
        String ciphertext = service.encrypt(plaintext);

        assertNotEquals(plaintext, ciphertext);
        assertTrue(ciphertext.startsWith("v1:"));
        assertTrue(service.canDecrypt(ciphertext));
        assertEquals(plaintext, service.decrypt(ciphertext));
        assertNotEquals(ciphertext, service.encrypt(plaintext));
    }

    @Test
    void decryptsLegacyCiphertextAndCanNormalizeItToVersionedFormat() throws Exception {
        EncryptionService service = new EncryptionService();
        ReflectionTestUtils.setField(service, "encryptionKey", "test-encryption-key-for-unit-tests");
        String plaintext = "legacy-auth-code";
        String legacyCiphertext = encryptLegacy(plaintext, "test-encryption-key-for-unit-tests");

        assertTrue(service.canDecrypt(legacyCiphertext));
        assertEquals(plaintext, service.decrypt(legacyCiphertext));

        String upgradedCiphertext = service.encrypt(service.decrypt(legacyCiphertext));
        assertTrue(upgradedCiphertext.startsWith("v1:"));
        assertEquals(plaintext, service.decrypt(upgradedCiphertext));
    }

    @Test
    void rejectsCiphertextShorterThanIvAndAuthenticationTag() {
        EncryptionService service = new EncryptionService();

        assertFalse(service.canDecrypt("YWJj"));
    }

    @Test
    void doesNotReturnVersionedCiphertextWhenAuthenticationFails() {
        EncryptionService writer = new EncryptionService();
        EncryptionService reader = new EncryptionService();
        ReflectionTestUtils.setField(writer, "encryptionKey", "writer-key-for-tests");
        ReflectionTestUtils.setField(reader, "encryptionKey", "different-reader-key");
        String ciphertext = writer.encrypt("auth-code");

        assertThrows(IllegalStateException.class, () -> reader.decrypt(ciphertext));
    }

    private static String encryptLegacy(String plaintext, String password) throws Exception {
        byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
        byte[] key = new byte[32];
        System.arraycopy(passwordBytes, 0, key, 0, Math.min(passwordBytes.length, key.length));
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length)
                .put(iv).put(encrypted).array());
    }
}
