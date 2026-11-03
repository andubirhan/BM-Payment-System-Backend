package com.example.postaxvatsystem.controller;

import com.example.postaxvatsystem.model.POSTransaction;
import com.example.postaxvatsystem.repository.POSTransactionReportRepository;
import com.example.postaxvatsystem.repository.POSTransactionSearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Additive performance endpoint; legacy reporting endpoints remain unchanged. */
@RestController
@RequestMapping("/api/v1/pos/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final POSTransactionReportRepository reports;
    private final POSTransactionSearchRepository searches;

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime previousDateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime previousDateTo) {
        Object[] aggregate = (Object[]) reports.getDashboardSummary(dateFrom, dateTo, previousDateFrom, previousDateTo);
        List<Map<String, Object>> paymentMethods = reports.getPaymentMethodBreakdown(dateFrom, dateTo).stream()
                .map(row -> Map.<String, Object>of("paymentMethod", row[0], "transactionCount", row[1], "totalAmount", row[2]))
                .toList();
        List<Map<String, Object>> recentTransactions = searches.findRecentTransactions(PageRequest.of(0, 5)).getContent().stream()
                .map(this::recentTransaction)
                .toList();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("current", metrics(aggregate, 0, 9, 10));
        response.put("previous", metrics(aggregate, 4, 11, 12));
        response.put("pendingCount", number(aggregate[8]).longValue());
        response.put("paymentMethods", paymentMethods);
        response.put("recentTransactions", recentTransactions);
        response.put("dateFrom", dateFrom);
        response.put("dateTo", dateTo);
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> metrics(Object[] values, int offset, int saleOffset, int purchaseOffset) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalRevenue", number(values[offset]).longValue());
        result.put("totalVAT", number(values[offset + 1]).longValue());
        result.put("totalTransactions", number(values[offset + 2]).longValue());
        result.put("avgTransactionValue", number(values[offset + 3]).doubleValue());
        result.put("netSales", number(values[saleOffset]).longValue());
        result.put("netPurchases", number(values[purchaseOffset]).longValue());
        return result;
    }

    private Map<String, Object> recentTransaction(POSTransaction transaction) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("transactionId", transaction.getTransactionId());
        result.put("customerName", transaction.getCustomerName());
        result.put("transactionType", transaction.getTransactionType());
        result.put("status", transaction.getStatus());
        result.put("netAmount", transaction.getNetAmount());
        result.put("createdAt", transaction.getCreatedAt());
        return result;
    }

    private Number number(Object value) {
        return value instanceof Number number ? number : 0;
    }
}