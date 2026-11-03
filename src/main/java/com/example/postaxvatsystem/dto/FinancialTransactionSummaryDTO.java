package com.example.postaxvatsystem.dto;

import com.example.postaxvatsystem.model.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class FinancialTransactionSummaryDTO {
    private TransactionType transactionType;
    private Long transactionCount;
    private BigDecimal grossAmount;
    private BigDecimal vatAmount;
    private BigDecimal serviceFeeAmount;
    private BigDecimal netAmount;
    private BigDecimal totalAmountPaid;
}
