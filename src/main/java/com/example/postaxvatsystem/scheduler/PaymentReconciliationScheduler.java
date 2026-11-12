package com.example.postaxvatsystem.scheduler;

import com.example.postaxvatsystem.client.BankApiClient;
import com.example.postaxvatsystem.dto.BankStatusResponse;
import com.example.postaxvatsystem.model.POSTransaction;
import com.example.postaxvatsystem.model.TransactionStatus;
import com.example.postaxvatsystem.repository.POSTransactionSearchRepository;
import com.example.postaxvatsystem.service.TransactionStateManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Asynchronous reconciliation scheduler that sweeps for records stranded by crashes.
 * 
 * Per BRD Requirement 5.1: Multi-threaded scheduling pool using Spring Boot's @EnableScheduling
 * Per BRD Requirement 5.2: Fixed interval of 5 minutes (300000ms)
 * Per BRD Requirement 5.3: 3-minute buffer window to avoid interfering with live payments
 * 
 * This scheduler handles both crash scenarios:
 * - Scenario 1: App crashes after bank responds but before DB commits
 * - Scenario 2: Network drops during response transport
 */
@Component
@Profile("!fixture")
@Slf4j
public class PaymentReconciliationScheduler {

    private final POSTransactionSearchRepository transactionRepository;
    private final BankApiClient bankApiClient;
    private final TransactionStateManager stateManager;

    public PaymentReconciliationScheduler(POSTransactionSearchRepository transactionRepository,
                                         BankApiClient bankApiClient,
                                         TransactionStateManager stateManager) {
        this.transactionRepository = transactionRepository;
        this.bankApiClient = bankApiClient;
        this.stateManager = stateManager;
    }

    /**
     * Scheduled task that runs every 5 minutes to reconcile stranded transactions.
     * 
     * Per BRD: fixedDelay = 300000ms (5 minutes)
     * Per BRD: 3-minute buffer window (createdAt <= SystemTime - 3 Minutes)
     * 
     * This prevents the engine from interfering with live payments that are actively processing.
     */
    @Scheduled(fixedDelay = 300000) // Wakes up every 5 minutes automatically
    public void sweepStrandedPayments() {
        log.info("Starting reconciliation sweep for stranded payments...");

        // Fetch items stuck for over 3 minutes to avoid race conditions with live users
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(3);
        List<POSTransaction> strandedTxns = transactionRepository
                .findStrandedPendingTransactions(TransactionStatus.PENDING, threshold);

        if (strandedTxns.isEmpty()) {
            log.info("No stranded pending transactions found.");
            return;
        }

        log.info("Found {} stranded pending transactions to reconcile.", strandedTxns.size());

        for (POSTransaction txn : strandedTxns) {
            try {
                log.info("Reconciling transaction: {} (Idempotency Key: {}, Status: {}, Created At: {}, Amount: {})",
                        txn.getTransactionId(), txn.getIdempotencyKey(), txn.getStatus(),
                        txn.getCreatedAt(), txn.getNetAmount());

                // Poll the bank to see if they processed this specific idempotency key
                BankStatusResponse bankStatus = bankApiClient.checkStatus(txn.getIdempotencyKey());

                String bankState = bankStatus == null || bankStatus.getStatus() == null
                        ? "UNKNOWN" : bankStatus.getStatus().toUpperCase();
                log.info("Bank status for {}: {}", txn.getTransactionId(), bankState);

                if ("SUCCESS".equals(bankState)) {
                    stateManager.finalizeToSuccess(txn.getId(), bankStatus.getRawResponseJson());
                    log.info("Transaction {} finalized to COMPLETED", txn.getTransactionId());
                } else if ("FAILED".equals(bankState) || "NOT_FOUND".equals(bankState)) {
                    stateManager.finalizeToFailure(txn.getId(), bankStatus.getRawResponseJson());
                    log.info("Transaction {} finalized to FAILED", txn.getTransactionId());
                } else {
                    log.warn("Bank returned an indeterminate status for {}; leaving it PENDING.", txn.getTransactionId());
                }            } catch (Exception e) {
                log.error("Error reconciling transaction: {} -> {}", 
                        txn.getTransactionId(), e.getMessage(), e);
                // Continue with next transaction rather than failing the entire batch
            }
        }

        log.info("Reconciliation sweep completed.");
    }
}
