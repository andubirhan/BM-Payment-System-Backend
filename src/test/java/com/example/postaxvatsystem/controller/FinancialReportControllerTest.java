package com.example.postaxvatsystem.controller;

import com.example.postaxvatsystem.dto.FinancialReportGroupDTO;
import com.example.postaxvatsystem.model.TransactionType;
import com.example.postaxvatsystem.repository.FinancialReportRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FinancialReportControllerTest {

    @Test
    void subtractsParentLinkedRefundsAndReversalsFromFinancialTotals() {
        FinancialReportRepository repository = mock(FinancialReportRepository.class);
        LocalDateTime from = LocalDateTime.of(2026, 10, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 10, 31, 23, 59);
        when(repository.summarizeFinancialActivity(from, to)).thenReturn(List.of(
                group(TransactionType.SALE, null, 1, "1000.00", "100.00", "20.00", "880.00", "880.00"),
                group(TransactionType.REFUND, TransactionType.SALE, 1, "200.00", "20.00", "0.00", "200.00", "0.00"),
                group(TransactionType.REVERSAL, TransactionType.SALE, 1, "100.00", "10.00", "2.00", "88.00", "0.00"),
                group(TransactionType.PURCHASE, null, 1, "500.00", "50.00", "10.00", "440.00", "440.00"),
                group(TransactionType.REVERSAL, TransactionType.PURCHASE, 1, "500.00", "50.00", "10.00", "440.00", "0.00")
        ));

        Map<?, ?> response = new FinancialReportController(repository)
                .getFinancialAuditReport(from, to)
                .getBody();
        Map<?, ?> analysis = (Map<?, ?>) response.get("analysis");

        assertMoney("700.00", analysis.get("netSalesAfterAdjustments"));
        assertMoney("0.00", analysis.get("netPurchasesAfterAdjustments"));
        assertMoney("70.00", analysis.get("salesVatRecordedAfterAdjustments"));
        assertMoney("0.00", analysis.get("purchasesVatRecordedAfterAdjustments"));
        assertMoney("18.00", analysis.get("serviceFeesAfterReversals"));
        assertMoney("0.00", analysis.get("salesPaymentVariance"));
        assertMoney("0.00", analysis.get("purchasesPaymentVariance"));
    }

    private FinancialReportGroupDTO group(
            TransactionType type,
            TransactionType parentType,
            long count,
            String gross,
            String vat,
            String serviceFee,
            String net,
            String paid) {
        return new FinancialReportGroupDTO(type, parentType, (long) count,
                new BigDecimal(gross), new BigDecimal(vat), new BigDecimal(serviceFee),
                new BigDecimal(net), new BigDecimal(paid));
    }

    private void assertMoney(String expected, Object actual) {
        assertEquals(0, new BigDecimal(expected).compareTo((BigDecimal) actual));
    }
}
