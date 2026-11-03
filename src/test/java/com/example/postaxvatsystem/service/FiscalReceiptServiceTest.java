package com.example.postaxvatsystem.service;

import com.example.postaxvatsystem.dto.FiscalReceiptResult;
import com.example.postaxvatsystem.model.POSTransaction;
import com.example.postaxvatsystem.model.TaxSubmissionStatus;
import com.example.postaxvatsystem.model.TaxTransaction;
import com.example.postaxvatsystem.model.TransactionStatus;
import com.example.postaxvatsystem.model.TransactionType;
import com.example.postaxvatsystem.repository.TaxTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FiscalReceiptServiceTest {

    @Mock
    private FiscalAuthorityClient authorityClient;

    @Mock
    private TaxTransactionRepository taxTransactionRepository;

    @Test
    void recordsRejectedReceiptWithoutChangingSuccessfulPaymentState() {
        POSTransaction transaction = settledSale();
        FiscalReceiptResult authorityResponse = new FiscalReceiptResult(
                null,
                null,
                TaxSubmissionStatus.REJECTED,
                "FIXTURE_INVALID_TAX_CATEGORY",
                "Simulated rejection",
                LocalDateTime.now(),
                "TEST-TAX-001",
                "FIXTURE");
        when(authorityClient.submitReceipt(transaction, false)).thenReturn(authorityResponse);
        when(taxTransactionRepository.save(any(TaxTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FiscalReceiptService service = new FiscalReceiptService(authorityClient, taxTransactionRepository);
        FiscalReceiptResult result = service.generateFiscalReceipt(transaction);

        assertEquals(TransactionStatus.SETTLED, transaction.getStatus());
        assertEquals(TaxSubmissionStatus.REJECTED, transaction.getFiscalSubmissionStatus());
        assertEquals("FIXTURE", transaction.getFiscalSubmissionMode());
        assertEquals("FIXTURE_INVALID_TAX_CATEGORY", transaction.getFiscalAuthorityResponseCode());
        assertEquals("Simulated rejection", transaction.getFiscalAuthorityResponseMessage());
        assertNull(transaction.getFiscalReference());
        assertNull(transaction.getFiscalSignature());
        assertEquals(authorityResponse, result);

        ArgumentCaptor<TaxTransaction> recordCaptor = ArgumentCaptor.forClass(TaxTransaction.class);
        verify(taxTransactionRepository).save(recordCaptor.capture());
        TaxTransaction saved = recordCaptor.getValue();
        assertEquals("TXN-SETTLED-001", saved.getPosTransactionId());
        assertEquals("ETB", saved.getCurrencyCode());
        assertEquals("FIXTURE", saved.getSubmissionMode());
        assertEquals(TaxSubmissionStatus.REJECTED, saved.getSubmissionStatus());
        assertEquals(Boolean.FALSE, saved.getIsCreditNote());
    }

    @Test
    void marksRefundReceiptsAsCreditNotesLinkedToTheirOriginalTransaction() {
        POSTransaction refund = settledSale();
        refund.setTransactionType(TransactionType.REFUND);
        refund.setParentTransactionId("TXN-ORIGINAL-001");
        when(authorityClient.submitReceipt(refund, true)).thenReturn(new FiscalReceiptResult(
                "TEST-FISCAL-001",
                "TEST-SIGNATURE-001",
                TaxSubmissionStatus.ACCEPTED,
                "FIXTURE_ACCEPTED",
                "Simulated acceptance",
                LocalDateTime.now(),
                "TEST-TAX-002",
                "FIXTURE"));
        when(taxTransactionRepository.save(any(TaxTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        new FiscalReceiptService(authorityClient, taxTransactionRepository).generateFiscalReceipt(refund);

        ArgumentCaptor<TaxTransaction> recordCaptor = ArgumentCaptor.forClass(TaxTransaction.class);
        verify(taxTransactionRepository).save(recordCaptor.capture());
        assertEquals(Boolean.TRUE, recordCaptor.getValue().getIsCreditNote());
        assertEquals("TXN-ORIGINAL-001", recordCaptor.getValue().getOriginalTransactionId());
    }

    private POSTransaction settledSale() {
        POSTransaction transaction = new POSTransaction();
        transaction.setTransactionId("TXN-SETTLED-001");
        transaction.setTransactionType(TransactionType.SALE);
        transaction.setStatus(TransactionStatus.SETTLED);
        transaction.setCurrencyCode("ETB");
        transaction.setGrossAmount(new BigDecimal("100.00"));
        transaction.setVatAmount(new BigDecimal("15.00"));
        transaction.setServiceFeeAmount(new BigDecimal("2.00"));
        transaction.setNetAmount(new BigDecimal("117.00"));
        return transaction;
    }
}
