package com.example.postaxvatsystem.dto;

import com.example.postaxvatsystem.model.TaxSubmissionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FiscalSubmissionReportDTO(
        String taxTransactionId,
        String posTransactionId,
        String authorityTransactionId,
        String fiscalReference,
        TaxSubmissionStatus submissionStatus,
        String submissionMode,
        BigDecimal grossAmount,
        BigDecimal vatAmount,
        BigDecimal serviceFeeAmount,
        BigDecimal netAmount,
        String currencyCode,
        String tin,
        String terminalId,
        LocalDateTime submittedToAuthorityAt,
        String authorityResponseCode,
        String authorityResponseMessage,
        Boolean isCreditNote,
        String originalTransactionId,
        LocalDateTime createdAt
) {
}
