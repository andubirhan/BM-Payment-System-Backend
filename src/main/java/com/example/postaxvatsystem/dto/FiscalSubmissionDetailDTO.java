package com.example.postaxvatsystem.dto;

import com.example.postaxvatsystem.model.TransactionType;

import java.math.BigDecimal;
import java.util.List;

public record FiscalSubmissionDetailDTO(
        FiscalSubmissionReportDTO submission,
        TransactionType transactionType,
        TransactionType businessTransactionType,
        String customerName,
        String customerTin,
        Boolean pricesIncludeVat,
        BigDecimal withholdingVatAmount,
        List<FiscalReceiptLineItemDTO> lineItems
) {
    public record FiscalReceiptLineItemDTO(
            String itemCode,
            String itemName,
            String itemDescription,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal discountAmount,
            String vatCategory,
            Boolean vatExempt,
            BigDecimal vatRate,
            BigDecimal grossAmount,
            BigDecimal vatAmount,
            BigDecimal serviceFeeAmount,
            BigDecimal netAmount
    ) {
    }
}
