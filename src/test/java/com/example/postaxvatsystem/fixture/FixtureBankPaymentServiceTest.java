package com.example.postaxvatsystem.fixture;

import com.example.postaxvatsystem.dto.BankStatusResponse;
import com.example.postaxvatsystem.dto.PaymentInitiationRequest;
import com.example.postaxvatsystem.model.POSTransaction;
import com.example.postaxvatsystem.model.TransactionType;
import com.example.postaxvatsystem.model.TransactionStatus;
import com.example.postaxvatsystem.repository.POSTransactionSearchRepository;
import com.example.postaxvatsystem.service.TransactionStateManager;
import com.example.postaxvatsystem.service.terminal.TerminalAuthorizationResult;
import com.example.postaxvatsystem.service.terminal.TerminalPaymentProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FixtureBankPaymentServiceTest {

    private static final String RAW_RESPONSE = "{\"status\":\"fixture\"}";

    @Mock
    private FixturePaymentDataProvider fixtures;

    @Mock
    private TransactionStateManager stateManager;

    @Mock
    private POSTransactionSearchRepository transactions;

    @Mock
    private TerminalPaymentProcessor terminalPaymentProcessor;

    @ParameterizedTest
    @ValueSource(strings = {"SUCCESS", "COMPLETED", "SETTLED", "AUTHORIZED"})
    void finalizesConfiguredSuccessfulStatuses(String status) {
        POSTransaction pending = pendingTransaction();
        POSTransaction completed = new POSTransaction();
        when(transactions.findByBefaPaymentId("BEFA-001")).thenReturn(List.of(pending));
        when(fixtures.bankStatus()).thenReturn(new BankStatusResponse(status, RAW_RESPONSE));
        when(stateManager.finalizeToSuccess(7L, RAW_RESPONSE)).thenReturn(completed);

        assertSame(completed, service().processBankPayment(request()));

        verify(stateManager).finalizeToSuccess(7L, RAW_RESPONSE);
        verify(stateManager, never()).finalizeToFailure(7L, RAW_RESPONSE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"FAILED", "REJECTED", "CANCELLED"})
    void finalizesConfiguredFailureStatuses(String status) {
        POSTransaction pending = pendingTransaction();
        POSTransaction failed = new POSTransaction();
        when(transactions.findByBefaPaymentId("BEFA-001")).thenReturn(List.of(pending));
        when(fixtures.bankStatus()).thenReturn(new BankStatusResponse(status, RAW_RESPONSE));
        when(stateManager.finalizeToFailure(7L, RAW_RESPONSE)).thenReturn(failed);

        assertSame(failed, service().processBankPayment(request()));

        verify(stateManager).finalizeToFailure(7L, RAW_RESPONSE);
        verify(stateManager, never()).finalizeToSuccess(7L, RAW_RESPONSE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNKNOWN", ""})
    void leavesUnknownAndEmptyStatusesPending(String status) {
        POSTransaction pending = pendingTransaction();
        when(transactions.findByBefaPaymentId("BEFA-001")).thenReturn(List.of(pending));
        when(fixtures.bankStatus()).thenReturn(new BankStatusResponse(status, RAW_RESPONSE));

        assertSame(pending, service().processBankPayment(request()));

        verify(stateManager, never()).finalizeToSuccess(7L, RAW_RESPONSE);
        verify(stateManager, never()).finalizeToFailure(7L, RAW_RESPONSE);
    }

    @Test
    void runsApprovedTerminalScenarioAndPersistsOnlyMaskedCardData() {
        POSTransaction pending = pendingTransaction();
        POSTransaction completed = new POSTransaction();
        PaymentInitiationRequest request = request();
        request.setTerminalScenarioId("APPROVED");
        request.setTransactionType(TransactionType.PURCHASE);
        when(transactions.findByBefaPaymentId("BEFA-001")).thenReturn(List.of());
        when(terminalPaymentProcessor.availableScenarios()).thenReturn(List.of(
                new com.example.postaxvatsystem.service.terminal.TerminalScenarioDTO(
                        "APPROVED", "Card approved", "Simulate issuer approval.")));
        when(fixtures.findBefaPayment("BEFA-001", TransactionType.PURCHASE)).thenReturn(new com.example.postaxvatsystem.dto.BefaPaymentDataDTO());
        when(stateManager.preparePendingTransaction(request,
                new com.example.postaxvatsystem.dto.BefaPaymentDataDTO())).thenReturn(pending);
        TerminalAuthorizationResult result = new TerminalAuthorizationResult(
                TerminalAuthorizationResult.Outcome.APPROVED, "Approved", "SIM-BANK-1",
                "SIM-AUTH-1", "SIM-RRN-1", "VISA", "4242");
        when(terminalPaymentProcessor.authorize(pending, "APPROVED")).thenReturn(result);
        when(stateManager.finalizeToSuccess(7L,
                "{\"status\":\"SUCCESS\",\"bankTransactionId\":\"SIM-BANK-1\"}")).thenReturn(completed);

        assertSame(completed, service().processBankPayment(request));

        verify(stateManager).recordTerminalAuthorization(7L, result);
        verify(terminalPaymentProcessor).authorize(pending, "APPROVED");
    }

    @Test
    void leavesTimedOutTerminalScenarioPendingForStatusCheck() {
        POSTransaction pending = pendingTransaction();
        PaymentInitiationRequest request = request();
        request.setTerminalScenarioId("PENDING");
        request.setTransactionType(TransactionType.PURCHASE);
        when(transactions.findByBefaPaymentId("BEFA-001")).thenReturn(List.of());
        when(terminalPaymentProcessor.availableScenarios()).thenReturn(List.of(
                new com.example.postaxvatsystem.service.terminal.TerminalScenarioDTO(
                        "PENDING", "No response", "Leave transaction pending.")));
        when(fixtures.findBefaPayment("BEFA-001", TransactionType.PURCHASE)).thenReturn(new com.example.postaxvatsystem.dto.BefaPaymentDataDTO());
        when(stateManager.preparePendingTransaction(request,
                new com.example.postaxvatsystem.dto.BefaPaymentDataDTO())).thenReturn(pending);
        TerminalAuthorizationResult result = new TerminalAuthorizationResult(
                TerminalAuthorizationResult.Outcome.PENDING, "No response", null,
                null, null, "VISA", "9995");
        when(terminalPaymentProcessor.authorize(pending, "PENDING")).thenReturn(result);
        when(stateManager.recordTerminalAuthorization(7L, result)).thenReturn(pending);

        assertSame(pending, service().processBankPayment(request));

        verify(stateManager, never()).finalizeToSuccess(7L, RAW_RESPONSE);
        verify(stateManager, never()).finalizeToFailure(7L, RAW_RESPONSE);
    }

    @Test
    void finalizesDeclinedTerminalScenarioAsFailure() {
        POSTransaction pending = pendingTransaction();
        POSTransaction declined = new POSTransaction();
        PaymentInitiationRequest request = request();
        request.setTerminalScenarioId("DECLINED");
        request.setTransactionType(TransactionType.PURCHASE);
        when(transactions.findByBefaPaymentId("BEFA-001")).thenReturn(List.of());
        when(terminalPaymentProcessor.availableScenarios()).thenReturn(List.of(
                new com.example.postaxvatsystem.service.terminal.TerminalScenarioDTO(
                        "DECLINED", "Card declined", "Simulate issuer decline.")));
        when(fixtures.findBefaPayment("BEFA-001", TransactionType.PURCHASE))
                .thenReturn(new com.example.postaxvatsystem.dto.BefaPaymentDataDTO());
        when(stateManager.preparePendingTransaction(request,
                new com.example.postaxvatsystem.dto.BefaPaymentDataDTO())).thenReturn(pending);
        TerminalAuthorizationResult result = new TerminalAuthorizationResult(
                TerminalAuthorizationResult.Outcome.DECLINED, "Issuer declined", null,
                null, null, "MASTERCARD", "0002");
        when(terminalPaymentProcessor.authorize(pending, "DECLINED")).thenReturn(result);
        when(stateManager.finalizeToFailure(7L, "Issuer declined")).thenReturn(declined);

        assertSame(declined, service().processBankPayment(request));

        verify(stateManager).recordTerminalAuthorization(7L, result);
        verify(stateManager).finalizeToFailure(7L, "Issuer declined");
        verify(stateManager, never()).finalizeToSuccess(7L, RAW_RESPONSE);
    }

    @Test
    void rejectsUnknownScenarioBeforeCreatingPendingTransaction() {
        PaymentInitiationRequest request = request();
        request.setTerminalScenarioId("INVALID");
        when(transactions.findByBefaPaymentId("BEFA-001")).thenReturn(List.of());
        when(terminalPaymentProcessor.availableScenarios()).thenReturn(List.of());

        assertThrows(IllegalArgumentException.class, () -> service().processBankPayment(request));

        verify(stateManager, never()).preparePendingTransaction(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(terminalPaymentProcessor, never()).authorize(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void doesNotReauthorizeAnExistingPendingTerminalTransaction() {
        POSTransaction pending = pendingTransaction();
        PaymentInitiationRequest request = request();
        request.setTerminalScenarioId("PENDING");
        when(transactions.findByBefaPaymentId("BEFA-001")).thenReturn(List.of(pending));

        assertSame(pending, service().processBankPayment(request));

        verify(terminalPaymentProcessor, never()).authorize(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private FixtureBankPaymentService service() {
        return new FixtureBankPaymentService(stateManager, null, transactions, fixtures, terminalPaymentProcessor);
    }

    private static PaymentInitiationRequest request() {
        PaymentInitiationRequest request = new PaymentInitiationRequest();
        request.setBefaPaymentId("BEFA-001");
        return request;
    }

    private static POSTransaction pendingTransaction() {
        POSTransaction transaction = new POSTransaction();
        transaction.setId(7L);
        transaction.setTransactionId("TXN-PENDING-001");
        transaction.setStatus(TransactionStatus.PENDING);
        return transaction;
    }
}
