package com.example.postaxvatsystem.service;

import com.example.postaxvatsystem.dto.PendingTransactionDTO;
import com.example.postaxvatsystem.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class POSTransactionServicePendingTest {

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
    @Mock private POSConfigurationService configurationService;

    @InjectMocks
    private POSTransactionService service;

    @Test
    void loadsPendingSummariesWithoutBindingNullDateParameters() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<PendingTransactionDTO> expected = new PageImpl<>(List.of());
        when(transactionRepository.findPendingTransactionSummaries(pageable)).thenReturn(expected);

        assertSame(expected, service.getPendingTransactions(null, null, pageable));

        verify(transactionRepository).findPendingTransactionSummaries(pageable);
        verifyNoMoreInteractions(transactionRepository);
    }

    @Test
    void loadsPendingSummariesUsingOnlyStartDate() {
        Pageable pageable = PageRequest.of(0, 20);
        LocalDateTime dateFrom = LocalDateTime.parse("2026-11-01T00:00:00");
        Page<PendingTransactionDTO> expected = new PageImpl<>(List.of());
        when(transactionRepository.findPendingTransactionSummariesFrom(dateFrom, pageable)).thenReturn(expected);

        assertSame(expected, service.getPendingTransactions(dateFrom, null, pageable));

        verify(transactionRepository).findPendingTransactionSummariesFrom(dateFrom, pageable);
        verifyNoMoreInteractions(transactionRepository);
    }

    @Test
    void loadsPendingSummariesUsingOnlyEndDate() {
        Pageable pageable = PageRequest.of(0, 20);
        LocalDateTime dateTo = LocalDateTime.parse("2026-11-03T23:59:59");
        Page<PendingTransactionDTO> expected = new PageImpl<>(List.of());
        when(transactionRepository.findPendingTransactionSummariesTo(dateTo, pageable)).thenReturn(expected);

        assertSame(expected, service.getPendingTransactions(null, dateTo, pageable));

        verify(transactionRepository).findPendingTransactionSummariesTo(dateTo, pageable);
        verifyNoMoreInteractions(transactionRepository);
    }

    @Test
    void loadsPendingSummariesWithinDateRange() {
        Pageable pageable = PageRequest.of(0, 20);
        LocalDateTime dateFrom = LocalDateTime.parse("2026-11-01T00:00:00");
        LocalDateTime dateTo = LocalDateTime.parse("2026-11-03T23:59:59");
        Page<PendingTransactionDTO> expected = new PageImpl<>(List.of());
        when(transactionRepository.findPendingTransactionSummariesBetween(dateFrom, dateTo, pageable))
                .thenReturn(expected);

        assertSame(expected, service.getPendingTransactions(dateFrom, dateTo, pageable));

        verify(transactionRepository).findPendingTransactionSummariesBetween(dateFrom, dateTo, pageable);
        verifyNoMoreInteractions(transactionRepository);
    }
}
