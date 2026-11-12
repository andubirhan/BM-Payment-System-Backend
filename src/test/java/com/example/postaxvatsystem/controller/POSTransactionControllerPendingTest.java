package com.example.postaxvatsystem.controller;

import com.example.postaxvatsystem.dto.PendingTransactionDTO;
import com.example.postaxvatsystem.model.TransactionType;
import com.example.postaxvatsystem.service.POSTransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class POSTransactionControllerPendingTest {

    private final POSTransactionService service = mock(POSTransactionService.class);
    private final POSTransactionController controller = new POSTransactionController(service);

    @Test
    void returnsSuccessfulEmptyPageWhenThereAreNoPendingTransactions() {
        Page<PendingTransactionDTO> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(service.getPendingTransactions(null, null, null, PageRequest.of(0, 20))).thenReturn(emptyPage);

        ResponseEntity<?> response = controller.getPendingTransactions(null, null, null, 0, 20);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(emptyPage, response.getBody());
    }

    @Test
    void rejectsAnInvertedDateRangeBeforeQuerying() {
        ResponseEntity<?> response = controller.getPendingTransactions(
                LocalDateTime.parse("2026-11-03T00:00:00"),
                LocalDateTime.parse("2026-11-01T00:00:00"),
                TransactionType.SALE,
                0,
                20);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(service, never()).getPendingTransactions(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void passesBusinessTransactionTypeToPendingSearch() {
        Page<PendingTransactionDTO> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(service.getPendingTransactions(null, null, TransactionType.PURCHASE, PageRequest.of(0, 20)))
                .thenReturn(page);

        ResponseEntity<?> response = controller.getPendingTransactions(
                null, null, TransactionType.PURCHASE, 0, 20);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(service).getPendingTransactions(null, null, TransactionType.PURCHASE, PageRequest.of(0, 20));
    }
}
