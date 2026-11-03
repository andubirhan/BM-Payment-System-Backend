package com.example.postaxvatsystem.dto;

import com.example.postaxvatsystem.model.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PendingTransactionDTO(
        String transactionId,
        String customerName,
        TransactionType transactionType,
        BigDecimal netAmount,
        String currencyCode,
        LocalDateTime createdAt) {
}
