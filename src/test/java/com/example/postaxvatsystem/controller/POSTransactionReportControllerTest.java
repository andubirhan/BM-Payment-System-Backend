package com.example.postaxvatsystem.controller;

import com.example.postaxvatsystem.repository.POSTransactionReportRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class POSTransactionReportControllerTest {

    @Test
    void returnsAggregatedRevenueAndGrossNetValues() {
        POSTransactionReportRepository repository = mock(POSTransactionReportRepository.class);
        LocalDateTime from = LocalDateTime.of(2026, 11, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 11, 3, 23, 59, 59);
        when(repository.calculateRevenueTotalsByDateRange(from, to)).thenReturn((List<Object[]>) List.of(new Object[]{
                new BigDecimal("900.50"),
                new BigDecimal("90.05"),
                new BigDecimal("18.01"),
                new BigDecimal("100.25"),
                new BigDecimal("990.55"),
        }));

        ResponseEntity<Map<String, Object>> response = new POSTransactionReportController(repository)
                .getRevenueReport(from, to);

        assertEquals(new BigDecimal("900.50"), response.getBody().get("revenue"));
        assertEquals(new BigDecimal("800.25"), response.getBody().get("netRevenue"));
        assertEquals(new BigDecimal("990.55"), response.getBody().get("grossAmount"));
        assertEquals(new BigDecimal("900.50"), response.getBody().get("netAmount"));
        assertEquals(new BigDecimal("18.01"), response.getBody().get("serviceFeeAmount"));
    }
}
