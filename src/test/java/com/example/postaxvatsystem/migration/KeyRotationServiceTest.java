package com.example.postaxvatsystem.migration;

import com.example.postaxvatsystem.security.EncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.List;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KeyRotationServiceTest {

    private static final String ENCRYPTION_KEY = "test-encryption-key-for-unit-tests";
    private JdbcTemplate jdbcTemplate;
    private EncryptionService encryptionService;
    private KeyRotationService service;
    private PreparedStatement updateStatement;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate = mock(JdbcTemplate.class);
        encryptionService = new EncryptionService();
        ReflectionTestUtils.setField(encryptionService, "encryptionKey", ENCRYPTION_KEY);

        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(DefaultTransactionDefinition.class)))
                .thenReturn(mock(TransactionStatus.class));
        service = new KeyRotationService(jdbcTemplate, encryptionService, transactionManager);
        updateStatement = mock(PreparedStatement.class);
    }

    @Test
    void migratesPlaintextInBoundedJdbcBatchAndUsesRawColumns() throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getLong("id")).thenReturn(12L);
        when(row.getString("auth_code")).thenReturn("AUTH-12");
        when(row.getString("rrn")).thenReturn(null);
        stubOnePage(row);
        stubBatchUpdate();

        KeyRotationService.MigrationSummary summary = service.migrateAllPaymentTenders();

        assertEquals(new KeyRotationService.MigrationSummary(1, 1, 0, 0), summary);
        verify(updateStatement).setString(2, null);
        verify(updateStatement).setLong(3, 12L);
        org.mockito.ArgumentCaptor<String> encryptedValue =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(updateStatement).setString(eq(1), encryptedValue.capture());
        assertTrue(encryptionService.canDecrypt(encryptedValue.getValue()));
        assertEquals("AUTH-12", encryptionService.decrypt(encryptedValue.getValue()));
    }

    @Test
    void skipsValuesAlreadyEncryptedWithTheCurrentKey() throws Exception {
        ResultSet row = mock(ResultSet.class);
        String currentCiphertext = encryptionService.encrypt("AUTH-13");
        when(row.getLong("id")).thenReturn(13L);
        when(row.getString("auth_code")).thenReturn(currentCiphertext);
        when(row.getString("rrn")).thenReturn(null);
        stubOnePage(row);

        KeyRotationService.MigrationSummary summary = service.migrateAllPaymentTenders();

        assertEquals(new KeyRotationService.MigrationSummary(1, 0, 1, 0), summary);
        verify(jdbcTemplate, never()).batchUpdate(anyString(), any(Collection.class), anyInt(), any());
    }

    @Test
    void migratesLegacyCiphertextWithoutDoubleEncryptingIt() throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getLong("id")).thenReturn(14L);
        when(row.getString("auth_code")).thenReturn(encryptLegacy("AUTH-14"));
        when(row.getString("rrn")).thenReturn(null);
        stubOnePage(row);
        stubBatchUpdate();

        KeyRotationService.MigrationSummary summary = service.migrateAllPaymentTenders();

        assertEquals(1, summary.migrated());
        org.mockito.ArgumentCaptor<String> encryptedValue =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(updateStatement).setString(eq(1), encryptedValue.capture());
        assertTrue(encryptionService.canDecrypt(encryptedValue.getValue()));
        assertEquals("AUTH-14", encryptionService.decrypt(encryptedValue.getValue()));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubOnePage(ResultSet row) throws Exception {
        doAnswer(invocation -> {
            long cursor = ((Number) invocation.getArgument(2)).longValue();
            if (cursor > 0) {
                return List.of();
            }
            RowMapper mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(row, 0));
        }).when(jdbcTemplate).query(anyString(), any(RowMapper.class), any(Object[].class));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubBatchUpdate() throws Exception {
        doAnswer(invocation -> {
            Collection updates = invocation.getArgument(1);
            ParameterizedPreparedStatementSetter setter = invocation.getArgument(3);
            setter.setValues(updateStatement, updates.iterator().next());
            return new int[][]{{1}};
        }).when(jdbcTemplate).batchUpdate(
                anyString(), any(Collection.class), anyInt(), any(ParameterizedPreparedStatementSetter.class));
    }

    private static String encryptLegacy(String plaintext) throws Exception {
        byte[] password = ENCRYPTION_KEY.getBytes(StandardCharsets.UTF_8);
        byte[] key = new byte[32];
        System.arraycopy(password, 0, key, 0, Math.min(password.length, key.length));
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ciphertext.length)
                .put(iv).put(ciphertext).array());
    }
}
