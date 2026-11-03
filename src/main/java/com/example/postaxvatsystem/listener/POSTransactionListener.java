package com.example.postaxvatsystem.listener;

import com.example.postaxvatsystem.model.POSTransaction;
import com.example.postaxvatsystem.security.HashService;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class POSTransactionListener {

    private static HashService hashService;

    @Autowired
    public void setHashService(HashService hashService) {
        POSTransactionListener.hashService = hashService;
    }

    @PrePersist
    @PreUpdate
    public void computeHashes(POSTransaction transaction) {
        if (hashService == null) {
            log.warn("HashService not initialized, skipping hash computation");
            return;
        }

        // Compute hash for customer TIN if present (case-insensitive)
        // customerName is no longer encrypted, so no hash needed
        if (transaction.getCustomerTin() != null && !transaction.getCustomerTin().isEmpty()) {
            transaction.setCustomerTinHash(hashService.hash(transaction.getCustomerTin().toLowerCase()));
        }
    }
}
