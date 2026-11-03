package com.example.postaxvatsystem.fixture;

import com.example.postaxvatsystem.dto.BefaPaymentDataDTO;
import com.example.postaxvatsystem.dto.PaymentInitiationRequest;
import com.example.postaxvatsystem.model.POSTransaction;
import com.example.postaxvatsystem.model.TransactionStatus;
import com.example.postaxvatsystem.repository.POSTransactionSearchRepository;
import com.example.postaxvatsystem.service.BankPaymentService;
import com.example.postaxvatsystem.service.TransactionStateManager;
import com.example.postaxvatsystem.service.terminal.TerminalAuthorizationResult;
import com.example.postaxvatsystem.service.terminal.TerminalPaymentProcessor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Executes the production checkpoint/finalization workflow against JSON third-party fixtures. */
@Service
@Profile("fixture")
public class FixtureBankPaymentService extends BankPaymentService {
    private static final Logger log = LoggerFactory.getLogger(FixtureBankPaymentService.class);
    private final FixturePaymentDataProvider fixtures;
    private final TransactionStateManager stateManager;
    private final POSTransactionSearchRepository transactions;
    private final TerminalPaymentProcessor terminalPaymentProcessor;

    public FixtureBankPaymentService(TransactionStateManager stateManager,
                                     com.example.postaxvatsystem.client.BankApiClient bankApiClient,
                                     POSTransactionSearchRepository transactions,
                                     FixturePaymentDataProvider fixtures,
                                     TerminalPaymentProcessor terminalPaymentProcessor) {
        super(stateManager, bankApiClient, transactions);
        this.fixtures = fixtures;
        this.stateManager = stateManager;
        this.transactions = transactions;
        this.terminalPaymentProcessor = terminalPaymentProcessor;
    }

    @Override
    public Object previewBefaPayment(PaymentInitiationRequest request) {
        return fixtures.preview(request.getBefaPaymentId(), request.getTransactionType());
    }

    @Override
    public POSTransaction processBankPayment(PaymentInitiationRequest request) {
        var existingTransactions = transactions.findByBefaPaymentId(request.getBefaPaymentId());
        if (!existingTransactions.isEmpty()) {
            POSTransaction existing = existingTransactions.get(0);
            if (existing.getStatus() != TransactionStatus.PENDING) {
                return existing;
            }
            if (request.getTerminalScenarioId() != null && !request.getTerminalScenarioId().isBlank()) {
                return existing;
            }
            return finalizeFromFixtureResponse(existing);
        }

        if (request.getTerminalScenarioId() != null
                && !request.getTerminalScenarioId().isBlank()
                && terminalPaymentProcessor.availableScenarios().stream()
                .noneMatch(scenario -> scenario.id().equalsIgnoreCase(request.getTerminalScenarioId().trim()))) {
            throw new IllegalArgumentException("Select a valid terminal test scenario.");
        }

        BefaPaymentDataDTO befa = fixtures.findBefaPayment(request.getBefaPaymentId(), request.getTransactionType());
        POSTransaction pending;
        try {
            pending = stateManager.preparePendingTransaction(request, befa);
        } catch (DataIntegrityViolationException duplicate) {
            var existingTxns = transactions.findByBefaPaymentId(request.getBefaPaymentId());
            if (!existingTxns.isEmpty()) {
                return existingTxns.get(0);
            }
            throw duplicate;
        }
        if (request.getTerminalScenarioId() != null && !request.getTerminalScenarioId().isBlank()) {
            return processTerminalScenario(pending, request.getTerminalScenarioId());
        }
        return finalizeFromFixtureResponse(pending);
    }

    private POSTransaction processTerminalScenario(POSTransaction pending, String scenarioId) {
        TerminalAuthorizationResult result = terminalPaymentProcessor.authorize(pending, scenarioId);
        POSTransaction updated = stateManager.recordTerminalAuthorization(pending.getId(), result);
        return switch (result.outcome()) {
            case APPROVED -> stateManager.finalizeToSuccess(
                    pending.getId(),
                    "{\"status\":\"SUCCESS\",\"bankTransactionId\":\"" + result.bankTransactionId() + "\"}");
            case DECLINED -> stateManager.finalizeToFailure(pending.getId(), result.message());
            case PENDING -> updated;
        };
    }

    private POSTransaction finalizeFromFixtureResponse(POSTransaction pending) {
        var bankResponse = fixtures.bankStatus();
        String status = bankResponse.getStatus() == null
                ? "" : bankResponse.getStatus().trim().toUpperCase(java.util.Locale.ROOT);
        return switch (status) {
            case "SUCCESS", "COMPLETED", "SETTLED", "AUTHORIZED", "AUTHORISED", "PAID" ->
                    stateManager.finalizeToSuccess(pending.getId(), bankResponse.getRawResponseJson());
            case "FAILED", "FAILURE", "REJECTED", "DECLINED", "CANCELLED", "CANCELED" ->
                    stateManager.finalizeToFailure(pending.getId(), bankResponse.getRawResponseJson());
            default -> {
                log.info("Bank fixture status '{}' leaves transaction {} pending.", status, pending.getTransactionId());
                yield pending;
            }
        };
    }
}