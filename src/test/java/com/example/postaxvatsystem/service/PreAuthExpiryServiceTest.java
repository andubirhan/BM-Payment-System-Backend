package com.example.postaxvatsystem.service;

import com.example.postaxvatsystem.model.POSTransaction;
import com.example.postaxvatsystem.model.TransactionStatus;
import com.example.postaxvatsystem.repository.POSTransactionRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class PreAuthExpiryServiceTest {

    @Test
    void continuesProcessingAfterOneExpiredPreAuthFails() {
        POSTransaction first = expiredTransaction("TXN-FAIL");
        POSTransaction second = expiredTransaction("TXN-NEXT");
        POSTransactionRepository repository = mock(POSTransactionRepository.class);
        POSTransactionService transactionService = mock(POSTransactionService.class);
        when(repository.findExpiredPreAuths(anyList(), any(LocalDateTime.class)))
                .thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("legacy database row"))
                .when(transactionService).expirePreAuth(eq("TXN-FAIL"), any(LocalDateTime.class));

        PreAuthExpiryService expiryService = new PreAuthExpiryService(repository, transactionService);
        expiryService.checkAndExpirePreAuths();

        verify(transactionService).expirePreAuth(eq("TXN-FAIL"), any(LocalDateTime.class));
        verify(transactionService).expirePreAuth(eq("TXN-NEXT"), any(LocalDateTime.class));
    }

    private static POSTransaction expiredTransaction(String transactionId) {
        POSTransaction transaction = new POSTransaction();
        transaction.setTransactionId(transactionId);
        transaction.setStatus(TransactionStatus.AUTHORIZED);
        return transaction;
    }
}
