package com.example.postaxvatsystem.service;

import com.example.postaxvatsystem.dto.CaptureRequestDTO;
import com.example.postaxvatsystem.dto.FiscalReceiptResult;
import com.example.postaxvatsystem.dto.ReversalRequestDTO;
import com.example.postaxvatsystem.dto.TransactionResponseDTO;
import com.example.postaxvatsystem.exception.InvalidTransactionStateException;
import com.example.postaxvatsystem.model.POSPaymentTender;
import com.example.postaxvatsystem.model.POSTransaction;
import com.example.postaxvatsystem.model.POSTransactionCapture;
import com.example.postaxvatsystem.model.TenderStatus;
import com.example.postaxvatsystem.model.TransactionStatus;
import com.example.postaxvatsystem.model.TransactionType;
import com.example.postaxvatsystem.repository.POSAuditLogRepository;
import com.example.postaxvatsystem.repository.POSPaymentTenderRepository;
import com.example.postaxvatsystem.repository.POSTransactionCaptureRepository;
import com.example.postaxvatsystem.repository.POSTransactionLineItemRepository;
import com.example.postaxvatsystem.repository.POSTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class POSTransactionServiceCaptureTest {

    @Mock private POSTransactionRepository transactionRepository;
    @Mock private POSTransactionLineItemRepository lineItemRepository;
    @Mock private POSPaymentTenderRepository paymentTenderRepository;
    @Mock private POSAuditLogRepository auditLogRepository;
    @Mock private VATCalculationService vatCalculationService;
    @Mock private FiscalReceiptService fiscalReceiptService;
    @Mock private ItemManagementService itemManagementService;
    @Mock private BankTransactionIdGenerator bankTransactionIdGenerator;
    @Mock private POSTransactionCaptureRepository captureRepository;
    @Mock private PreAuthorizationOperations preAuthorizationOperations;

    @InjectMocks
    private POSTransactionService service;

    @Test
    void partialManualCaptureClosesAuthorizationAndPreventsAnotherManualCapture() {
        POSTransaction transaction = authorizedSalePreAuth();
        when(transactionRepository.findByTransactionIdForUpdate(transaction.getTransactionId()))
                .thenReturn(Optional.of(transaction));
        when(transactionRepository.save(any(POSTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(captureRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(captureRepository.save(any(POSTransactionCapture.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(preAuthorizationOperations.capture(any(), any(BigDecimal.class), anyString()))
                .thenReturn(new GatewayOperationResult("SIM-CAPTURE", "SIMULATED_APPROVED"));
        when(preAuthorizationOperations.release(any(), any(BigDecimal.class), anyString()))
                .thenReturn(new GatewayOperationResult("SIM-RELEASE", "SIMULATED_APPROVED"));
        when(lineItemRepository.findByTransactionId(transaction.getId())).thenReturn(List.of());
        when(bankTransactionIdGenerator.nextId()).thenReturn("BANK-TXN-TEST-1");
        when(fiscalReceiptService.generateFiscalReceipt(transaction))
                .thenReturn(new FiscalReceiptResult("FISCAL-1", "SIGNATURE-1"));

        TransactionResponseDTO partial = service.capturePreAuth(
                transaction.getTransactionId(),
                new CaptureRequestDTO("manual-1", money("40.00")),
                "cashier-1",
                "CASHIER");

        assertEquals(TransactionStatus.PARTIALLY_CAPTURED, partial.getStatus());
        assertEquals(TransactionType.SALE, partial.getTransactionType());
        assertMoney("40.00", partial.getTotalAmountPaid());
        assertMoney("40.00", transaction.getPaymentTenders().get(0).getAmountApplied());
        assertMoney("60.00", transaction.getPreauthHoldAmount().subtract(transaction.getTotalAmountPaid()));
        verify(preAuthorizationOperations).capture(transaction, money("40.00"), "manual-1");
        verify(preAuthorizationOperations).release(transaction, money("60.00"), "manual-1:release");
        verify(fiscalReceiptService).generateFiscalReceipt(transaction);
        verify(itemManagementService).applySuccessfulTransaction(transaction);

        assertThrows(InvalidTransactionStateException.class, () -> service.capturePreAuth(
                transaction.getTransactionId(),
                new CaptureRequestDTO("manual-2", money("10.00")),
                "cashier-1",
                "CASHIER"));
        verify(preAuthorizationOperations, times(1)).capture(any(), any(BigDecimal.class), anyString());
    }

    @Test
    void retryingTheSameCaptureIsIdempotent() {
        POSTransaction transaction = authorizedSalePreAuth();
        POSTransactionCapture firstCapture = new POSTransactionCapture();
        firstCapture.setTransaction(transaction);
        firstCapture.setAmount(money("40.00"));
        firstCapture.setCumulativeAmount(money("40.00"));
        when(transactionRepository.findByTransactionIdForUpdate(transaction.getTransactionId()))
                .thenReturn(Optional.of(transaction));
        when(captureRepository.findByIdempotencyKey("manual-1")).thenReturn(Optional.of(firstCapture));

        TransactionResponseDTO response = service.capturePreAuth(
                transaction.getTransactionId(),
                new CaptureRequestDTO("manual-1", money("40.00")),
                "cashier-1",
                "CASHIER");

        assertEquals(TransactionStatus.AUTHORIZED, response.getStatus());
        verify(preAuthorizationOperations, never()).capture(any(), any(BigDecimal.class), anyString());
    }

    @Test
    void expiryCapturesConfiguredAmountAndReleasesTheRemainingHold() {
        POSTransaction transaction = authorizedSalePreAuth();
        transaction.setAutoCaptureAmount(money("10.00"));
        transaction.setPreauthExpiresAt(LocalDateTime.now().minusMinutes(1));
        stubSuccessfulExpiry(transaction);

        TransactionResponseDTO expired = service.expirePreAuth(
                transaction.getTransactionId(), LocalDateTime.now());

        assertEquals(TransactionStatus.CAPTURED, expired.getStatus());
        assertMoney("10.00", expired.getTotalAmountPaid());
        assertMoney("10.00", expired.getRefundableAmount());
        verify(preAuthorizationOperations).capture(
                transaction, money("10.00"), "auto-expiry:" + transaction.getTransactionId());
        verify(preAuthorizationOperations).release(
                transaction, money("90.00"), "auto-expiry:" + transaction.getTransactionId() + ":release");
        verify(fiscalReceiptService).generateFiscalReceipt(transaction);
    }

    @Test
    void zeroAutomaticCaptureReleasesTheWholeHold() {
        POSTransaction transaction = authorizedSalePreAuth();
        transaction.setAutoCaptureAmount(BigDecimal.ZERO);
        transaction.setPreauthExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(transactionRepository.findByTransactionIdForUpdate(transaction.getTransactionId()))
                .thenReturn(Optional.of(transaction));
        when(transactionRepository.save(any(POSTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(preAuthorizationOperations.release(any(), any(BigDecimal.class), anyString()))
                .thenReturn(new GatewayOperationResult("SIM-RELEASE", "SIMULATED_APPROVED"));

        TransactionResponseDTO expired = service.expirePreAuth(
                transaction.getTransactionId(), LocalDateTime.now());

        assertEquals(TransactionStatus.EXPIRED, expired.getStatus());
        assertMoney("0.00", expired.getTotalAmountPaid());
        verify(preAuthorizationOperations).release(
                transaction, money("100.00"), "auto-expiry:" + transaction.getTransactionId());
        verify(fiscalReceiptService, never()).generateFiscalReceipt(transaction);
    }

    @Test
    void cancellationReleasesAnUncapturedAuthorizationIdempotently() {
        POSTransaction transaction = authorizedSalePreAuth();
        when(transactionRepository.findByTransactionIdForUpdate(transaction.getTransactionId()))
                .thenReturn(Optional.of(transaction));
        when(transactionRepository.save(any(POSTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(preAuthorizationOperations.release(
                eq(transaction), eq(money("100.00")), eq("void-key")))
                .thenReturn(new GatewayOperationResult("SIM-VOID", "SIMULATED_APPROVED"));

        ReversalRequestDTO request = new ReversalRequestDTO("void-key", "Order cancelled");
        TransactionResponseDTO cancelled = service.cancelPreAuth(
                transaction.getTransactionId(), request, "manager-1", "MANAGER");

        assertEquals(TransactionStatus.CANCELLED, cancelled.getStatus());
        assertEquals(TransactionStatus.CANCELLED, service.cancelPreAuth(
                transaction.getTransactionId(), request, "manager-1", "MANAGER").getStatus());
        verify(preAuthorizationOperations, times(1)).release(
                transaction, money("100.00"), "void-key");
    }

    private void stubSuccessfulExpiry(POSTransaction transaction) {
        when(transactionRepository.findByTransactionIdForUpdate(transaction.getTransactionId()))
                .thenReturn(Optional.of(transaction));
        when(transactionRepository.save(any(POSTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(captureRepository.save(any(POSTransactionCapture.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(preAuthorizationOperations.capture(any(), any(BigDecimal.class), anyString()))
                .thenReturn(new GatewayOperationResult("SIM-AUTO-CAPTURE", "SIMULATED_APPROVED"));
        when(preAuthorizationOperations.release(any(), any(BigDecimal.class), anyString()))
                .thenReturn(new GatewayOperationResult("SIM-RELEASE", "SIMULATED_APPROVED"));
        when(lineItemRepository.findByTransactionId(transaction.getId())).thenReturn(List.of());
        when(bankTransactionIdGenerator.nextId()).thenReturn("BANK-TXN-AUTO-1");
        when(fiscalReceiptService.generateFiscalReceipt(transaction))
                .thenReturn(new FiscalReceiptResult("FISCAL-AUTO", "SIGNATURE-AUTO"));
    }

    private static POSTransaction authorizedSalePreAuth() {
        POSTransaction transaction = new POSTransaction();
        transaction.setId(1L);
        transaction.setTransactionId("TXN-PREAUTH-1");
        transaction.setTransactionType(TransactionType.PRE_AUTH);
        transaction.setBusinessTransactionType(TransactionType.SALE);
        transaction.setStatus(TransactionStatus.AUTHORIZED);
        transaction.setPreauthHoldAmount(money("100.00"));
        transaction.setAutoCaptureAmount(money("10.00"));
        transaction.setPreauthExpiresAt(LocalDateTime.now().plusHours(1));
        transaction.setGrossAmount(money("100.00"));
        transaction.setVatAmount(BigDecimal.ZERO);
        transaction.setServiceFeeAmount(BigDecimal.ZERO);
        transaction.setNetAmount(money("100.00"));
        transaction.setTotalAmountPaid(BigDecimal.ZERO);
        transaction.setRefundableAmount(BigDecimal.ZERO);
        transaction.setManualCaptureOccurred(false);

        POSPaymentTender tender = new POSPaymentTender();
        tender.setAmountTendered(money("100.00"));
        tender.setAmountApplied(BigDecimal.ZERO);
        tender.setStatus(TenderStatus.AUTHORIZED);
        transaction.setPaymentTenders(List.of(tender));
        transaction.setLineItems(List.of());
        return transaction;
    }

    private static BigDecimal money(String amount) {
        return new BigDecimal(amount);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, money(expected).compareTo(actual));
    }
}
