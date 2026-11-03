package com.example.postaxvatsystem.controller;

import com.example.postaxvatsystem.dto.FiscalSubmissionReportDTO;
import com.example.postaxvatsystem.dto.FiscalSubmissionDetailDTO;
import com.example.postaxvatsystem.model.POSTransaction;
import com.example.postaxvatsystem.model.POSTransactionLineItem;
import com.example.postaxvatsystem.model.TaxSubmissionStatus;
import com.example.postaxvatsystem.model.TaxTransaction;
import com.example.postaxvatsystem.model.TransactionType;
import com.example.postaxvatsystem.repository.POSTransactionRepository;
import com.example.postaxvatsystem.repository.TaxTransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.mockito.ArgumentMatchers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FiscalSubmissionReportControllerTest {

    @Test
    void returnsFiscalSubmissionRowsInAPaginatedReport() {
        TaxTransactionRepository repository = mock(TaxTransactionRepository.class);
        POSTransactionRepository posRepository = mock(POSTransactionRepository.class);
        TaxTransaction submission = new TaxTransaction();
        submission.setTaxTransactionId("TAX-REPORT-001");
        submission.setPosTransactionId("POS-REPORT-001");
        submission.setAuthorityTransactionId("TEST-AUTHORITY-001");
        submission.setSubmissionStatus(TaxSubmissionStatus.ACCEPTED);
        submission.setSubmissionMode("FIXTURE");
        submission.setGrossAmount(new BigDecimal("100.00"));
        submission.setVatAmount(new BigDecimal("15.00"));
        submission.setServiceFeeAmount(new BigDecimal("0.00"));
        submission.setNetAmount(new BigDecimal("115.00"));
        submission.setCurrencyCode("ETB");
        submission.setIsCreditNote(false);
        submission.setCreatedAt(LocalDateTime.of(2026, 11, 2, 10, 0));
        when(repository.findAll(ArgumentMatchers.<Specification<TaxTransaction>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(submission), PageRequest.of(0, 20,
                        Sort.by(Sort.Direction.DESC, "createdAt")), 1));

        var response = new FiscalSubmissionReportController(repository, posRepository)
                .getFiscalSubmissions(null, null, "ACCEPTED", "FIXTURE", false,
                        "TAX-REPORT-001", 0, 20);

        Page<?> report = (Page<?>) response.getBody();
        FiscalSubmissionReportDTO row = (FiscalSubmissionReportDTO) report.getContent().get(0);
        assertEquals(1, report.getTotalElements());
        assertEquals("TAX-REPORT-001", row.taxTransactionId());
        assertEquals("POS-REPORT-001", row.posTransactionId());
        assertEquals("FIXTURE", row.submissionMode());
        assertEquals(new BigDecimal("15.00"), row.vatAmount());
        verify(repository).findAll(ArgumentMatchers.<Specification<TaxTransaction>>any(), any(Pageable.class));
    }

    @Test
    void rejectsInvalidDateRangeAndPageSize() {
        TaxTransactionRepository repository = mock(TaxTransactionRepository.class);
        POSTransactionRepository posRepository = mock(POSTransactionRepository.class);
        FiscalSubmissionReportController controller = new FiscalSubmissionReportController(repository, posRepository);

        var invalidDateRange = controller.getFiscalSubmissions(
                LocalDateTime.of(2026, 11, 3, 0, 0),
                LocalDateTime.of(2026, 11, 2, 0, 0),
                null, null, null, null, 0, 20);
        var invalidPageSize = controller.getFiscalSubmissions(
                null, null, null, null, null, null, 0, 101);

        assertEquals(400, invalidDateRange.getStatusCode().value());
        assertEquals(400, invalidPageSize.getStatusCode().value());
        assertEquals("dateFrom must be on or before dateTo", invalidDateRange.getBody());
        assertEquals("page must be non-negative and size must be between 1 and 100", invalidPageSize.getBody());
        verify(repository, never()).findAll(ArgumentMatchers.<Specification<TaxTransaction>>any(), any(Pageable.class));
    }

    @Test
    void returnsReceiptLinesForTheFiscalRecord() {
        TaxTransactionRepository taxRepository = mock(TaxTransactionRepository.class);
        POSTransactionRepository posRepository = mock(POSTransactionRepository.class);
        TaxTransaction fiscal = new TaxTransaction();
        fiscal.setTaxTransactionId("TAX-RECEIPT-001");
        fiscal.setPosTransactionId("POS-RECEIPT-001");
        fiscal.setFiscalReference("TEST-FISCAL-001");
        fiscal.setSubmissionStatus(TaxSubmissionStatus.ACCEPTED);
        fiscal.setSubmissionMode("FIXTURE");
        fiscal.setGrossAmount(new BigDecimal("100.00"));
        fiscal.setVatAmount(new BigDecimal("15.00"));
        fiscal.setServiceFeeAmount(new BigDecimal("0.00"));
        fiscal.setNetAmount(new BigDecimal("115.00"));
        fiscal.setCurrencyCode("ETB");

        POSTransaction transaction = new POSTransaction();
        transaction.setTransactionId("POS-RECEIPT-001");
        transaction.setTransactionType(TransactionType.SALE);
        POSTransactionLineItem item = new POSTransactionLineItem();
        item.setItemCode("ITEM-001");
        item.setItemName("Test item");
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("50.00"));
        item.setLineGrossAmount(new BigDecimal("100.00"));
        item.setLineVatAmount(new BigDecimal("15.00"));
        item.setLineServiceFeeAmount(new BigDecimal("0.00"));
        item.setLineNetAmount(new BigDecimal("115.00"));
        transaction.setLineItems(List.of(item));
        when(taxRepository.findByTaxTransactionId("TAX-RECEIPT-001")).thenReturn(Optional.of(fiscal));
        when(posRepository.findByTransactionId("POS-RECEIPT-001")).thenReturn(Optional.of(transaction));

        var response = new FiscalSubmissionReportController(taxRepository, posRepository)
                .getFiscalSubmissionDetail("TAX-RECEIPT-001");

        assertEquals(200, response.getStatusCode().value());
        FiscalSubmissionDetailDTO detail = (FiscalSubmissionDetailDTO) response.getBody();
        assertEquals("TEST-FISCAL-001", detail.submission().fiscalReference());
        assertEquals("TAX-RECEIPT-001", detail.submission().taxTransactionId());
        assertEquals(1, detail.lineItems().size());
        assertEquals("Test item", detail.lineItems().get(0).itemName());
        assertEquals(2, detail.lineItems().get(0).quantity());
    }
}
