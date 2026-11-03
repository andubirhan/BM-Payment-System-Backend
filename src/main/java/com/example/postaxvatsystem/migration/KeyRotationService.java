package com.example.postaxvatsystem.migration;

import com.example.postaxvatsystem.security.EncryptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class KeyRotationService {

    private static final String SELECT_BATCH = """
            SELECT id, auth_code, rrn
            FROM pos_payment_tenders
            WHERE id > ?
            ORDER BY id
            LIMIT ?
            """;
    private static final String UPDATE_FIELDS = """
            UPDATE pos_payment_tenders
            SET auth_code = ?, rrn = ?
            WHERE id = ?
            """;
    private static final int BATCH_SIZE = 250;

    private final JdbcTemplate jdbcTemplate;
    private final EncryptionService encryptionService;
    private final TransactionTemplate transactionTemplate;

    public KeyRotationService(
            JdbcTemplate jdbcTemplate,
            EncryptionService encryptionService,
            PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.encryptionService = encryptionService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public MigrationSummary migrateAllPaymentTenders() {
        log.info("Starting batched payment tender encryption migration");
        long lastId = 0;
        long scanned = 0;
        long migrated = 0;
        long skipped = 0;
        long errors = 0;

        while (true) {
            Long cursor = lastId;
            BatchResult batch = transactionTemplate.execute(status -> migrateBatch(cursor));
            if (batch == null) {
                throw new IllegalStateException("Payment tender migration batch returned no result.");
            }
            if (batch.rowsRead() == 0) {
                break;
            }
            lastId = batch.lastId();
            scanned += batch.rowsRead();
            migrated += batch.migrated();
            skipped += batch.skipped();
            errors += batch.errors();
            log.info("Processed {} payment tenders; migrated {}, skipped {}, errors {}",
                    scanned, migrated, skipped, errors);
        }

        MigrationSummary summary = new MigrationSummary(scanned, migrated, skipped, errors);
        log.info("Payment tender encryption migration finished: {}", summary);
        return summary;
    }

    private BatchResult migrateBatch(long afterId) {
        List<RawTender> tenders = jdbcTemplate.query(SELECT_BATCH, TENDER_ROW_MAPPER, afterId, BATCH_SIZE);
        if (tenders.isEmpty()) {
            return new BatchResult(afterId, 0, 0, 0, 0);
        }

        List<EncryptedTenderUpdate> updates = new ArrayList<>();
        long skipped = 0;
        long errors = 0;
        for (RawTender tender : tenders) {
            try {
                FieldResult authCode = migrateField(tender.authCode());
                FieldResult rrn = migrateField(tender.rrn());
                if (authCode.changed() || rrn.changed()) {
                    updates.add(new EncryptedTenderUpdate(
                            authCode.value(), rrn.value(), tender.id()));
                } else {
                    skipped++;
                }
            } catch (RuntimeException exception) {
                errors++;
                log.error("Unable to migrate payment tender ID {}. The row was left unchanged.",
                        tender.id(), exception);
            }
        }

        if (!updates.isEmpty()) {
            jdbcTemplate.batchUpdate(UPDATE_FIELDS, updates, updates.size(), (statement, update) -> {
                statement.setString(1, update.authCode());
                statement.setString(2, update.rrn());
                statement.setLong(3, update.id());
            });
        }

        return new BatchResult(
                tenders.get(tenders.size() - 1).id(),
                tenders.size(),
                updates.size(),
                skipped,
                errors);
    }

    private FieldResult migrateField(String value) {
        if (value == null || value.isEmpty() || encryptionService.isCurrentFormat(value)) {
            return new FieldResult(value, false);
        }

        if (encryptionService.hasEncryptedPayloadShape(value)) {
            String decryptedValue = encryptionService.decrypt(value);
            if (value.equals(decryptedValue)) {
                throw new IllegalStateException(
                        "Value resembles ciphertext but cannot be authenticated with supported keys.");
            }
            return new FieldResult(encryptionService.encrypt(decryptedValue), true);
        }
        return new FieldResult(encryptionService.encrypt(value), true);
    }

    private static final RowMapper<RawTender> TENDER_ROW_MAPPER = (resultSet, rowNumber) ->
            new RawTender(
                    resultSet.getLong("id"),
                    resultSet.getString("auth_code"),
                    resultSet.getString("rrn"));

    public record MigrationSummary(long scanned, long migrated, long skipped, long errors) {
    }

    private record RawTender(Long id, String authCode, String rrn) {
    }

    private record EncryptedTenderUpdate(String authCode, String rrn, Long id) {
    }

    private record FieldResult(String value, boolean changed) {
    }

    private record BatchResult(long lastId, long rowsRead, long migrated, long skipped, long errors) {
    }
}
