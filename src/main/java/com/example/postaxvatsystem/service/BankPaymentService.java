package com.example.postaxvatsystem.service;

import com.example.postaxvatsystem.client.BankApiClient;
import com.example.postaxvatsystem.dto.BefaPaymentDataDTO;
import com.example.postaxvatsystem.dto.PaymentInitiationRequest;
import com.example.postaxvatsystem.model.POSTransaction;
import com.example.postaxvatsystem.repository.POSTransactionSearchRepository;
import com.example.postaxvatsystem.exception.DuplicateTransactionException;
import org.springframework.context.annotation.Profile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;

import java.io.IOException;
import java.net.SocketTimeoutException;

/**
 * Core banking orchestrator that manages the payment lifecycle with crash resilience.
 * Implements the exact sequence: Save PENDING -> Call Bank -> Update Status
 * 
 * This service handles both normal completion and crash scenarios:
 * - Scenario 1: App crashes after bank responds but before DB commits
 * - Scenario 2: Network drops during response transport
 */
@Service
@Profile("!fixture")
@Slf4j
public class BankPaymentService {

    private final TransactionStateManager stateManager;
    private final BankApiClient bankApiClient;
    private final POSTransactionSearchRepository transactionRepository;

    public BankPaymentService(TransactionStateManager stateManager, BankApiClient bankApiClient, POSTransactionSearchRepository transactionRepository) {
        this.stateManager = stateManager;
        this.bankApiClient = bankApiClient;
        this.transactionRepository = transactionRepository;
    }

    /**
     * Processes a bank payment with crash resilience.
     *
     * Sequence:
     * 1. Check for existing transaction by BEFA payment ID (safe retry)
     * 2. Fetch BEFA-Private data
     * 3. Save PENDING to DB (isolated transaction)
     * 4. Call Bank API with idempotency key
     * 5. Update to SETTLED/FAILED
     *
     * If crash occurs at step 5, the reconciliation scheduler will recover it.
     *
     * @param request Payment initiation request
     * @return Transaction object (may be PENDING if network failure occurred)
     */
    public POSTransaction processBankPayment(PaymentInitiationRequest request) {
        if (request.getTerminalScenarioId() != null && !request.getTerminalScenarioId().isBlank()) {
            throw new IllegalArgumentException("Terminal test scenarios are only available with the fixture profile.");
        }
        log.info("Starting bank payment processing for BEFA ID: {}", request.getBefaPaymentId());

        // Step 1: Check if transaction already exists for this BEFA payment ID (safe retry)
        var existingTransactions = transactionRepository.findByBefaPaymentId(request.getBefaPaymentId());
        if (!existingTransactions.isEmpty()) {
            POSTransaction existing = existingTransactions.get(0);
            log.warn("Duplicate transaction detected for BEFA ID: {}. Existing transaction: {} with status: {}",
                    request.getBefaPaymentId(), existing.getTransactionId(), existing.getStatus());
            throw new DuplicateTransactionException(
                    "Transaction with BEFA payment ID " + request.getBefaPaymentId() + " has already been processed",
                    request.getBefaPaymentId(),
                    existing.getTransactionId(),
                    existing.getStatus().name()
            );
        }

        // Step 2: Simulate data acquisition from internal BEFA-Private records
        BefaPaymentDataDTO internalBefaData = fetchPrivateBefaRecords(request.getBefaPaymentId());

        // Step 2: Persist state map configuration into database as PENDING
        // This uses Propagation.REQUIRES_NEW to ensure commit before network call
        POSTransaction pendingTransaction;
        try {
            pendingTransaction = stateManager.preparePendingTransaction(request, internalBefaData);
        } catch (DataIntegrityViolationException duplicate) {
            // A concurrent request won the unique BEFA-payment constraint.
            var existingTxns = transactionRepository.findByBefaPaymentId(request.getBefaPaymentId());
            if (!existingTxns.isEmpty()) {
                return existingTxns.get(0);
            }
            throw duplicate;
        }

        try {
            // Step 3: Call external bank interface using the generated tracking keys
            log.info("Calling bank API for transaction: {}", pendingTransaction.getTransactionId());
            String rawBankResponse = bankApiClient.sendPayment(
                    pendingTransaction.getIdempotencyKey(),
                    pendingTransaction.getNetAmount().subtract(
                            pendingTransaction.getWithholdingVatAmount() == null
                                    ? java.math.BigDecimal.ZERO : pendingTransaction.getWithholdingVatAmount()),
                    pendingTransaction.getCurrencyCode(),
                    "Tax-VAT payment matching ID " + pendingTransaction.getTransactionId()
            );

            // Step 4: Normal Completion Flow
            log.info("Bank API call successful. Finalizing to SETTLED.");
            return stateManager.finalizeToSuccess(pendingTransaction.getId(), rawBankResponse);

        } catch (Exception ex) {
            // Scenario 2 Trap: If communication breaks, DO NOT modify local status fields.
            // Leave it PENDING so the background recovery worker can reconcile it with the bank later.
            
            Throwable cause = ex.getCause();
            if (cause instanceof SocketTimeoutException || ex instanceof IOException) {
                log.warn("Communication crash caught for ID {}. Left PENDING for reconciliation. Error: {}", 
                        pendingTransaction.getTransactionId(), ex.getMessage());
                return pendingTransaction;
            }

            // If it's a validation error or standard decline, mark it FAILED immediately.
            log.error("Bank payment failed for transaction: {}. Error: {}", 
                    pendingTransaction.getTransactionId(), ex.getMessage());
            return stateManager.finalizeToFailure(pendingTransaction.getId(), ex.getMessage());
        }
    }

    /**
     * Fetches payment data from the internal BEFA-Private secure repository.
     * In production, this would call an internal API or service.
     * 
     * @param befaPaymentId BEFA payment reference ID
     * @return BefaPaymentDataDTO with payment details
     */
    /**
     * Retrieves review-only payment data from the private BEFA boundary.
     * This does not create a transaction or contact the bank.
     */
    public Object previewBefaPayment(PaymentInitiationRequest request) {
        return fetchPrivateBefaRecords(request.getBefaPaymentId());
    }
    private BefaPaymentDataDTO fetchPrivateBefaRecords(String befaPaymentId) {
        throw new IllegalStateException(
                "No BEFA data source is configured. Activate the fixture profile for local JSON fixtures " +
                "or configure the BEFA-Private integration.");
    }
}
