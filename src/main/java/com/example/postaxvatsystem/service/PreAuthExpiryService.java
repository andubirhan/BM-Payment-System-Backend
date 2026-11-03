package com.example.postaxvatsystem.service;

import com.example.postaxvatsystem.model.TransactionStatus;
import com.example.postaxvatsystem.repository.POSTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PreAuthExpiryService {

    private final POSTransactionRepository transactionRepository;
    private final POSTransactionService transactionService;

    @Scheduled(fixedRateString = "#{${preauth.expiry.check.interval.minutes} * 60 * 1000}")
    public void checkAndExpirePreAuths() {
        log.info("Checking for expired pre-authorizations");

        List<String> expiredTransactionIds = transactionRepository.findExpiredPreAuths(
                List.of(TransactionStatus.AUTHORIZED, TransactionStatus.PARTIALLY_CAPTURED),
                LocalDateTime.now()
        ).stream().map(transaction -> transaction.getTransactionId()).toList();

        int expiredCount = 0;
        int skippedCount = 0;

        for (String transactionId : expiredTransactionIds) {
            try {
                transactionService.expirePreAuth(transactionId, LocalDateTime.now());
                expiredCount++;
            } catch (Exception e) {
                log.warn("Skipping expired pre-auth transaction {} after processing failed: {}",
                        transactionId, e.getMessage());
                log.debug("Pre-auth expiry failure details for transaction {}", transactionId, e);
                skippedCount++;
            }
        }

        log.info("Expired {} pre-authorizations, skipped {} due to errors", expiredCount, skippedCount);
    }
}
