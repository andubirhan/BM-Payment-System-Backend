package com.example.postaxvatsystem.service;

import com.example.postaxvatsystem.dto.FiscalReceiptResult;
import com.example.postaxvatsystem.model.POSTransaction;
import com.example.postaxvatsystem.model.TaxTransaction;
import com.example.postaxvatsystem.repository.TaxTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FiscalReceiptService {
    private final FiscalAuthorityClient fiscalAuthorityClient;
    private final TaxTransactionRepository taxTransactionRepository;

    public FiscalReceiptResult generateFiscalReceipt(POSTransaction transaction) {
        boolean creditNote = transaction.getTransactionType()
                == com.example.postaxvatsystem.model.TransactionType.REFUND;
        FiscalReceiptResult result = fiscalAuthorityClient.submitReceipt(transaction, creditNote);
        transaction.setFiscalReference(result.fiscalReference());
        transaction.setFiscalSignature(result.fiscalSignature());
        transaction.setFiscalSubmissionStatus(result.submissionStatus());
        transaction.setFiscalSubmissionMode(result.submissionMode());
        transaction.setFiscalAuthorityResponseCode(result.authorityResponseCode());
        transaction.setFiscalAuthorityResponseMessage(result.authorityResponseMessage());
        transaction.setFiscalSubmittedToAuthorityAt(result.submittedToAuthorityAt());

        TaxTransaction submission = new TaxTransaction();
        submission.setTaxTransactionId("TAX-" + UUID.randomUUID());
        submission.setAuthorityTransactionId(result.authorityTransactionId());
        submission.setPosTransactionId(transaction.getTransactionId());
        submission.setGrossAmount(transaction.getGrossAmount());
        submission.setVatAmount(transaction.getVatAmount());
        submission.setServiceFeeAmount(transaction.getServiceFeeAmount());
        submission.setNetAmount(transaction.getNetAmount());
        submission.setCurrencyCode(transaction.getCurrencyCode());
        submission.setSubmissionMode(result.submissionMode());
        submission.setFiscalSignature(result.fiscalSignature());
        submission.setFiscalReference(result.fiscalReference());
        submission.setTin(transaction.getCustomerTin());
        submission.setTerminalId(transaction.getCashRegisterId());
        submission.setSubmittedToAuthorityAt(result.submittedToAuthorityAt());
        submission.setAuthorityResponseCode(result.authorityResponseCode());
        submission.setAuthorityResponseMessage(result.authorityResponseMessage());
        submission.setSubmissionStatus(result.submissionStatus());
        submission.setIsCreditNote(creditNote);
        submission.setOriginalTransactionId(creditNote ? transaction.getParentTransactionId() : null);
        taxTransactionRepository.save(submission);

        log.info("Recorded fiscal receipt result for transaction {} with status {} in {} mode",
                transaction.getTransactionId(), result.submissionStatus(), result.submissionMode());
        return result;
    }
}
