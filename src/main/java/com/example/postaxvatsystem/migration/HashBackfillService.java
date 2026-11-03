package com.example.postaxvatsystem.migration;

import com.example.postaxvatsystem.model.POSTransaction;
import com.example.postaxvatsystem.repository.POSTransactionRepository;
import com.example.postaxvatsystem.security.EncryptionService;
import com.example.postaxvatsystem.security.HashService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class HashBackfillService {

    private final POSTransactionRepository transactionRepository;
    private final EncryptionService encryptionService;
    private final HashService hashService;

    /**
     * Backfills hash columns for existing transactions.
     * This should be run once after adding the hash columns to the database.
     */
    @Transactional
    public void backfillHashes() {
        log.info("Starting hash backfill for existing transactions...");

        List<POSTransaction> allTransactions = transactionRepository.findAll();
        log.info("Found {} transactions to process", allTransactions.size());

        int updatedCount = 0;
        int skippedCount = 0;

        for (POSTransaction transaction : allTransactions) {
            boolean needsUpdate = false;

            // customerName is no longer encrypted, skip processing

            // Process customer TIN
            if (transaction.getCustomerTin() != null && !transaction.getCustomerTin().isEmpty()) {
                String decryptedTin = encryptionService.decrypt(transaction.getCustomerTin());
                if (decryptedTin != null && !decryptedTin.isEmpty()) {
                    String tinHash = hashService.hash(decryptedTin.toLowerCase());
                    if (!tinHash.equals(transaction.getCustomerTinHash())) {
                        transaction.setCustomerTinHash(tinHash);
                        needsUpdate = true;
                    }
                }
            }

            if (needsUpdate) {
                transactionRepository.save(transaction);
                updatedCount++;
                if (updatedCount % 100 == 0) {
                    log.info("Updated {} transactions so far...", updatedCount);
                }
            } else {
                skippedCount++;
            }
        }

        log.info("Hash backfill completed. Updated: {}, Skipped: {}", updatedCount, skippedCount);
    }
}
