package com.example.postaxvatsystem.service;

import com.example.postaxvatsystem.dto.FiscalReceiptResult;
import com.example.postaxvatsystem.model.POSTransaction;
import com.example.postaxvatsystem.model.TaxSubmissionStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!fixture")
public class UnconfiguredFiscalAuthorityClient implements FiscalAuthorityClient {
    @Override
    public FiscalReceiptResult submitReceipt(POSTransaction transaction, boolean creditNote) {
        return new FiscalReceiptResult(
                null,
                null,
                TaxSubmissionStatus.FAILED,
                "AUTHORITY_NOT_CONFIGURED",
                "No fiscal authority integration is configured; payment processing is not changed.",
                null,
                null,
                "NOT_CONFIGURED"
        );
    }
}
