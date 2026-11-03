package com.example.postaxvatsystem.dto;

import com.example.postaxvatsystem.model.TaxSubmissionStatus;

import java.time.LocalDateTime;

public record FiscalReceiptResult(
        String fiscalReference,
        String fiscalSignature,
        TaxSubmissionStatus submissionStatus,
        String authorityResponseCode,
        String authorityResponseMessage,
        LocalDateTime submittedToAuthorityAt,
        String authorityTransactionId,
        String submissionMode
) {
    public FiscalReceiptResult(String fiscalReference, String fiscalSignature) {
        this(fiscalReference, fiscalSignature, TaxSubmissionStatus.ACCEPTED,
                "LEGACY", "Legacy receipt result", null, null, "LEGACY");
    }
}
