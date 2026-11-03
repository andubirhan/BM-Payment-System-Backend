package com.example.postaxvatsystem.repository;

import com.example.postaxvatsystem.model.POSTransactionCapture;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface POSTransactionCaptureRepository extends JpaRepository<POSTransactionCapture, Long> {
    Optional<POSTransactionCapture> findByIdempotencyKey(String idempotencyKey);
    List<POSTransactionCapture> findByTransaction_IdOrderByCreatedAtAsc(Long transactionId);
}
