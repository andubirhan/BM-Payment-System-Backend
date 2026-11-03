package com.example.postaxvatsystem.service;

import com.example.postaxvatsystem.dto.FiscalReceiptResult;
import com.example.postaxvatsystem.dto.RefundRequestDTO;
import com.example.postaxvatsystem.dto.TransactionResponseDTO;
import com.example.postaxvatsystem.exception.InvalidTransactionStateException;
import com.example.postaxvatsystem.exception.RefundAmountExceededException;
import com.example.postaxvatsystem.model.POSTransaction;
import com.example.postaxvatsystem.model.TransactionStatus;
import com.example.postaxvatsystem.model.TransactionType;
import com.example.postaxvatsystem.repository.POSAuditLogRepository;
import com.example.postaxvatsystem.repository.POSPaymentTenderRepository;
import com.example.postaxvatsystem.repository.POSTransactionCaptureRepository;
import com.example.postaxvatsystem.repository.POSTransactionLineItemRepository;
import com.example.postaxvatsystem.repository.POSTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class POSTransactionServiceRefundTest {

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

    @BeforeEach
    void setRefundWindow() {
        ReflectionTestUtils.setField(service, "refundWindowDays", 30);
    }

    @Test
    void refundsFromCapturedAmountWhenLegacyRecordHasNoRefundableAmount() {
        POSTransaction original = settledSale();
        original.setRefundableAmount(null);
        when(transactionRepository.findByTransactionIdForUpdate(original.getTransactionId()))
                .thenReturn(Optional.of(original));
        when(transactionRepository.findByIdempotencyKey("refund-key"))
                .thenReturn(Optional.empty());
        when(transactionRepository.sumRefundedAmountByParentTransactionId(original.getTransactionId()))
                .thenReturn(BigDecimal.ZERO);
        when(vatCalculationService.calculateRefundWithVat(
                eq(money("1000.00")), eq(BigDecimal.ZERO), eq(BigDecimal.ZERO),
                eq(money("1000.00")), eq(money("400.00"))))
                .thenReturn(new VATCalculationService.RefundCalculation(
                        money("400.00"), money("0.00"), money("0.00"), money("400.00"), BigDecimal.ZERO));
        when(transactionRepository.save(any(POSTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(fiscalReceiptService.generateFiscalReceipt(any(POSTransaction.class)))
                .thenReturn(new FiscalReceiptResult("FISCAL-REFUND", "SIGNATURE-REFUND"));
        when(bankTransactionIdGenerator.nextId()).thenReturn("BANK-REFUND-1");

        TransactionResponseDTO response = service.processRefund(
                original.getTransactionId(),
                new RefundRequestDTO("refund-key", money("400.00"), null, "Customer request"),
                "cashier-1",
                "CASHIER");

        assertEquals(TransactionStatus.REFUNDED, response.getStatus());
        assertEquals(original.getTransactionId(), response.getParentTransactionId());
        assertMoney("400.00", response.getNetAmount());
        assertNotEquals(original.getBefaPaymentId(), response.getBefaPaymentId());
        assertEquals(TransactionStatus.PARTIALLY_REFUNDED, original.getStatus());
        assertMoney("400.00", original.getRefundedAmount());
        assertMoney("1000.00", original.getNetAmount());
    }

    @Test
    void doesNotRefundMoreThanTheConfirmedCapturedAmount() {
        POSTransaction original = settledSale();
        original.setRefundableAmount(money("1500.00"));
        when(transactionRepository.findByTransactionIdForUpdate(original.getTransactionId()))
                .thenReturn(Optional.of(original));
        when(transactionRepository.findByIdempotencyKey("refund-too-large"))
                .thenReturn(Optional.empty());
        when(transactionRepository.sumRefundedAmountByParentTransactionId(original.getTransactionId()))
                .thenReturn(BigDecimal.ZERO);

        assertThrows(RefundAmountExceededException.class, () -> service.processRefund(
                original.getTransactionId(),
                new RefundRequestDTO("refund-too-large", money("1001.00"), null, null),
                "cashier-1",
                "CASHIER"));
    }

    @Test
    void doesNotAllowRefundsForPurchaseTransactions() {
        POSTransaction original = settledSale();
        original.setTransactionType(TransactionType.PURCHASE);
        original.setBusinessTransactionType(TransactionType.PURCHASE);
        when(transactionRepository.findByTransactionIdForUpdate(original.getTransactionId()))
                .thenReturn(Optional.of(original));
        when(transactionRepository.findByIdempotencyKey("purchase-refund-key"))
                .thenReturn(Optional.empty());

        assertThrows(InvalidTransactionStateException.class, () -> service.processRefund(
                original.getTransactionId(),
                new RefundRequestDTO("purchase-refund-key", money("100.00"), null, "Supplier return"),
                "cashier-1",
                "CASHIER"));

        verify(transactionRepository, never()).sumRefundedAmountByParentTransactionId(any());
        verify(transactionRepository, never()).save(any(POSTransaction.class));
        verify(vatCalculationService, never()).calculateRefundWithVat(any(), any(), any(), any(), any());
        verify(fiscalReceiptService, never()).generateFiscalReceipt(any(POSTransaction.class));
    }

    @Test
    void refundsPartiallyCapturedTransactionsAgainstTheirCapturedBalance() {
        POSTransaction original = settledSale();
        original.setStatus(TransactionStatus.PARTIALLY_CAPTURED);
        original.setNetAmount(money("400.00"));
        original.setTotalAmountPaid(money("400.00"));
        original.setRefundableAmount(money("400.00"));
        when(transactionRepository.findByTransactionIdForUpdate(original.getTransactionId()))
                .thenReturn(Optional.of(original));
        when(transactionRepository.findByIdempotencyKey("partial-refund-key"))
                .thenReturn(Optional.empty());
        when(transactionRepository.sumRefundedAmountByParentTransactionId(original.getTransactionId()))
                .thenReturn(BigDecimal.ZERO);
        when(vatCalculationService.calculateRefundWithVat(
                any(), any(), any(), any(), any()))
                .thenReturn(new VATCalculationService.RefundCalculation(
                        money("100.00"), BigDecimal.ZERO, BigDecimal.ZERO, money("100.00"), BigDecimal.ZERO));
        when(transactionRepository.save(any(POSTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(fiscalReceiptService.generateFiscalReceipt(any(POSTransaction.class)))
                .thenReturn(new FiscalReceiptResult("FISCAL-PARTIAL-REFUND", "SIGNATURE-PARTIAL-REFUND"));
        when(bankTransactionIdGenerator.nextId()).thenReturn("BANK-PARTIAL-REFUND");

        TransactionResponseDTO response = service.processRefund(
                original.getTransactionId(),
                new RefundRequestDTO("partial-refund-key", money("100.00"), null, null),
                "cashier-1",
                "CASHIER");

        assertEquals(TransactionStatus.REFUNDED, response.getStatus());
        assertEquals(TransactionStatus.PARTIALLY_REFUNDED, original.getStatus());
        assertMoney("100.00", original.getRefundedAmount());
    }

    @Test
    void sameIdempotencyKeyAndRequestReturnsTheExistingRefundBeforeRecheckingBalance() {
        POSTransaction original = settledSale();
        original.setStatus(TransactionStatus.REFUNDED);
        POSTransaction existingRefund = new POSTransaction();
        existingRefund.setId(2L);
        existingRefund.setTransactionId("TXN-REFUND-1");
        existingRefund.setParentTransactionId(original.getTransactionId());
        existingRefund.setTransactionType(TransactionType.REFUND);
        existingRefund.setStatus(TransactionStatus.REFUNDED);
        existingRefund.setIdempotencyKey("refund-key");
        existingRefund.setBefaPaymentId("REFUND-TXN-REFUND-1");
        existingRefund.setNetAmount(money("1000.00"));
        existingRefund.setGrossAmount(money("1000.00"));
        existingRefund.setVatAmount(BigDecimal.ZERO);
        existingRefund.setServiceFeeAmount(BigDecimal.ZERO);
        existingRefund.setTotalAmountPaid(BigDecimal.ZERO);
        when(transactionRepository.findByTransactionIdForUpdate(original.getTransactionId()))
                .thenReturn(Optional.of(original));
        when(transactionRepository.findByIdempotencyKey("refund-key"))
                .thenReturn(Optional.of(existingRefund));

        TransactionResponseDTO response = service.processRefund(
                original.getTransactionId(),
                new RefundRequestDTO("refund-key", money("1000.00"), null, null),
                "cashier-1",
                "CASHIER");

        assertEquals(existingRefund.getTransactionId(), response.getTransactionId());
        verify(transactionRepository, never()).sumRefundedAmountByParentTransactionId(any());
    }

    private static POSTransaction settledSale() {
        POSTransaction transaction = new POSTransaction();
        transaction.setId(1L);
        transaction.setTransactionId("TXN-SETTLED-1");
        transaction.setTransactionType(TransactionType.SALE);
        transaction.setStatus(TransactionStatus.SETTLED);
        transaction.setCashRegisterId("REGISTER-1");
        transaction.setCashierId("cashier-1");
        transaction.setIdempotencyKey("sale-key");
        transaction.setBefaPaymentId("BEFA-SALE-1");
        transaction.setCurrencyCode("ETB");
        transaction.setGrossAmount(money("1000.00"));
        transaction.setVatAmount(BigDecimal.ZERO);
        transaction.setServiceFeeAmount(BigDecimal.ZERO);
        transaction.setNetAmount(money("1000.00"));
        transaction.setTotalAmountPaid(money("1000.00"));
        transaction.setRefundedAmount(BigDecimal.ZERO);
        transaction.setCreatedAt(LocalDateTime.now());
        return transaction;
    }

    private static BigDecimal money(String amount) {
        return new BigDecimal(amount);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, money(expected).compareTo(actual));
    }
}
