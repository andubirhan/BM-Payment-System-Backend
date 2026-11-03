package com.example.postaxvatsystem.controller;

import com.example.postaxvatsystem.repository.POSTransactionReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/pos/reports")
@RequiredArgsConstructor
@Slf4j
public class POSTransactionReportController {

    private final POSTransactionReportRepository reportRepository;

    @GetMapping("/revenue")
    public ResponseEntity<Map<String, Object>> getRevenueReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo) {
        
        log.info("Generating revenue report from {} to {}", dateFrom, dateTo);
        
        Object[] totals = reportRepository.calculateRevenueTotalsByDateRange(dateFrom, dateTo);
        BigDecimal revenue = asBigDecimal(totals[0]);
        BigDecimal vat = asBigDecimal(totals[1]);
        BigDecimal serviceFees = asBigDecimal(totals[2]);
        BigDecimal refunds = asBigDecimal(totals[3]);
        BigDecimal gross = asBigDecimal(totals[4]);
        
        Map<String, Object> response = new HashMap<>();
        response.put("revenue", revenue);
        response.put("vatAmount", vat);
        response.put("serviceFees", serviceFees);
        response.put("refunds", refunds);
        response.put("netRevenue", revenue.subtract(refunds));
        response.put("grossAmount", gross);
        response.put("netAmount", revenue);
        response.put("serviceFeeAmount", serviceFees);
        response.put("dateFrom", dateFrom);
        response.put("dateTo", dateTo);
        
        return ResponseEntity.ok(response);
    }

    private BigDecimal asBigDecimal(Object value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
    }

    @GetMapping("/cashier-performance")
    public ResponseEntity<?> getCashierPerformance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo) {
        
        log.info("Generating cashier performance report from {} to {}", dateFrom, dateTo);
        List<Object[]> results = reportRepository.getCashierPerformance(dateFrom, dateTo);
        if (results.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "No cashier performance data found for the specified date range",
                                "dateFrom", dateFrom, "dateTo", dateTo));
        }
        List<Map<String, Object>> labeledResults = results.stream()
            .map(row -> Map.of(
                "cashierId", row[0],
                "transactionCount", row[1],
                "totalRevenue", row[2]
            ))
            .toList();
        return ResponseEntity.ok(labeledResults);
    }

    @GetMapping("/payment-method-breakdown")
    public ResponseEntity<?> getPaymentMethodBreakdown(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo) {
        
        log.info("Generating payment method breakdown from {} to {}", dateFrom, dateTo);
        List<Object[]> results = reportRepository.getPaymentMethodBreakdown(dateFrom, dateTo);
        if (results.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "No payment method breakdown data found for the specified date range",
                                "dateFrom", dateFrom, "dateTo", dateTo));
        }
        List<Map<String, Object>> labeledResults = results.stream()
            .map(row -> Map.of(
                "paymentMethod", row[0],
                "transactionCount", row[1],
                "totalAmount", row[2]
            ))
            .toList();
        return ResponseEntity.ok(labeledResults);
    }

    @GetMapping("/daily-summary")
    public ResponseEntity<?> getDailySummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo) {
        
        log.info("Generating daily summary from {} to {}", dateFrom, dateTo);
        List<Object[]> results = reportRepository.getDailySummary(dateFrom, dateTo);
        if (results.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "No daily summary data found for the specified date range",
                                "dateFrom", dateFrom, "dateTo", dateTo));
        }
        List<Map<String, Object>> labeledResults = results.stream()
            .map(row -> Map.of(
                "date", row[0],
                "transactionCount", row[1],
                "totalRevenue", row[2],
                "totalVAT", row[3]
            ))
            .toList();
        return ResponseEntity.ok(labeledResults);
    }

    @GetMapping("/monthly-summary")
    public ResponseEntity<?> getMonthlySummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo) {
        
        log.info("Generating monthly summary from {} to {}", dateFrom, dateTo);
        List<Object[]> results = reportRepository.getMonthlySummary(dateFrom, dateTo);
        if (results.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "No monthly summary data found for the specified date range",
                                "dateFrom", dateFrom, "dateTo", dateTo));
        }
        List<Map<String, Object>> labeledResults = results.stream()
            .map(row -> Map.of(
                "month", row[0],
                "year", row[1],
                "transactionCount", row[2],
                "totalRevenue", row[3],
                "totalVAT", row[4]
            ))
            .toList();
        return ResponseEntity.ok(labeledResults);
    }

    @GetMapping("/transaction-stats")
    public ResponseEntity<Map<String, Object>> getTransactionStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo) {
        
        log.info("Generating transaction stats from {} to {}", dateFrom, dateTo);
        
        Long totalCount = reportRepository.getTotalTransactionsCount(dateFrom, dateTo);
        Long completedCount = reportRepository.getCompletedTransactionsCount(dateFrom, dateTo);
        Long refundedCount = reportRepository.getRefundedTransactionsCount(dateFrom, dateTo);
        Long reversedCount = reportRepository.getReversedTransactionsCount(dateFrom, dateTo);
        Double avgValue = reportRepository.getAverageTransactionValue(dateFrom, dateTo);
        Double refundRate = reportRepository.getRefundRate(dateFrom, dateTo);
        
        // Handle null refund rate when there are no completed transactions
        if (refundRate == null) {
            refundRate = 0.0;
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("totalTransactions", totalCount);
        response.put("completedTransactions", completedCount);
        response.put("refundedTransactions", refundedCount);
        response.put("reversedTransactions", reversedCount);
        response.put("averageTransactionValue", avgValue);
        response.put("refundRate", refundRate);
        response.put("dateFrom", dateFrom);
        response.put("dateTo", dateTo);
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/top-cashiers")
    public ResponseEntity<?> getTopCashiersByRevenue(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo) {
        
        log.info("Generating top cashiers report from {} to {}", dateFrom, dateTo);
        List<Object[]> results = reportRepository.getTopCashiersByRevenue(dateFrom, dateTo);
        if (results.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "No top cashiers data found for the specified date range",
                                "dateFrom", dateFrom, "dateTo", dateTo));
        }
        List<Map<String, Object>> labeledResults = results.stream()
            .map(row -> Map.of(
                "cashierId", row[0],
                "transactionCount", row[1],
                "totalRevenue", row[2]
            ))
            .toList();
        return ResponseEntity.ok(labeledResults);
    }

    @GetMapping("/top-registers")
    public ResponseEntity<?> getTopRegistersByRevenue(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo) {
        
        log.info("Generating top registers report from {} to {}", dateFrom, dateTo);
        List<Object[]> results = reportRepository.getTopRegistersByRevenue(dateFrom, dateTo);
        if (results.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "No top registers data found for the specified date range",
                                "dateFrom", dateFrom, "dateTo", dateTo));
        }
        List<Map<String, Object>> labeledResults = results.stream()
            .map(row -> Map.of(
                "cashRegisterId", row[0],
                "transactionCount", row[1],
                "totalRevenue", row[2]
            ))
            .toList();
        return ResponseEntity.ok(labeledResults);
    }

    @GetMapping("/vat")
    public ResponseEntity<Map<String, Object>> getVATReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo) {
        
        log.info("Generating VAT report from {} to {}", dateFrom, dateTo);
        Long vat = reportRepository.calculateVATByDateRange(dateFrom, dateTo);
        
        Map<String, Object> response = new HashMap<>();
        response.put("vatAmount", vat);
        response.put("dateFrom", dateFrom);
        response.put("dateTo", dateTo);
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/service-fees")
    public ResponseEntity<Map<String, Object>> getServiceFeesReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo) {
        
        log.info("Generating service fees report from {} to {}", dateFrom, dateTo);
        Long serviceFees = reportRepository.calculateServiceFeesByDateRange(dateFrom, dateTo);
        
        Map<String, Object> response = new HashMap<>();
        response.put("serviceFees", serviceFees);
        response.put("dateFrom", dateFrom);
        response.put("dateTo", dateTo);
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/refunds")
    public ResponseEntity<Map<String, Object>> getRefundsReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo) {
        
        log.info("Generating refunds report from {} to {}", dateFrom, dateTo);
        Long refunds = reportRepository.calculateTotalRefundsByDateRange(dateFrom, dateTo);
        
        Map<String, Object> response = new HashMap<>();
        response.put("refunds", refunds);
        response.put("dateFrom", dateFrom);
        response.put("dateTo", dateTo);
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/register-reconciliation/{cashRegisterId}")
    public ResponseEntity<?> getRegisterReconciliation(
            @PathVariable String cashRegisterId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo) {
        
        log.info("Generating register reconciliation for {} from {} to {}", cashRegisterId, dateFrom, dateTo);
        List<com.example.postaxvatsystem.model.POSTransaction> results = reportRepository.findByCashRegisterIdAndDateRange(cashRegisterId, dateFrom, dateTo);
        if (results.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "No transactions found for the specified cash register and date range",
                                "cashRegisterId", cashRegisterId, "dateFrom", dateFrom, "dateTo", dateTo));
        }
        return ResponseEntity.ok(results);
    }

    @GetMapping("/failed-transactions")
    public ResponseEntity<?> getFailedTransactions(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo) {
        
        log.info("Generating failed transactions report from {} to {}", dateFrom, dateTo);
        List<com.example.postaxvatsystem.model.POSTransaction> results = reportRepository.findFailedTransactions(dateFrom, dateTo);
        if (results.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "No failed transactions found for the specified date range",
                                "dateFrom", dateFrom, "dateTo", dateTo));
        }
        return ResponseEntity.ok(results);
    }

    @GetMapping("/unsettled-transactions")
    public ResponseEntity<?> getUnsettledTransactions(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime cutoffDate) {
        
        log.info("Generating unsettled transactions report with cutoff date {}", cutoffDate);
        List<com.example.postaxvatsystem.model.POSTransaction> results = reportRepository.findUnsettledTransactions(cutoffDate);
        if (results.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "No unsettled transactions found before the specified cutoff date",
                                "cutoffDate", cutoffDate));
        }
        return ResponseEntity.ok(results);
    }

    @GetMapping("/high-value-transactions")
    public ResponseEntity<?> getHighValueTransactions(
            @RequestParam Long threshold) {
        
        log.info("Generating high value transactions report with threshold {}", threshold);
        List<com.example.postaxvatsystem.model.POSTransaction> results = reportRepository.findHighValueTransactions(threshold);
        if (results.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "No high value transactions found above the specified threshold",
                                "threshold", threshold));
        }
        return ResponseEntity.ok(results);
    }

    @GetMapping("/transaction-count-by-status")
    public ResponseEntity<?> getTransactionCountByStatus(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo) {
        
        log.info("Generating transaction count by status from {} to {}", dateFrom, dateTo);
        List<Object[]> results = reportRepository.getTransactionCountByStatus(dateFrom, dateTo);
        if (results.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "No transaction count by status data found for the specified date range",
                                "dateFrom", dateFrom, "dateTo", dateTo));
        }
        List<Map<String, Object>> labeledResults = results.stream()
            .map(row -> Map.of(
                "status", row[0],
                "transactionCount", row[1]
            ))
            .toList();
        return ResponseEntity.ok(labeledResults);
    }

    @GetMapping("/hourly-volume")
    public ResponseEntity<?> getHourlyTransactionVolume(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo) {
        
        log.info("Generating hourly transaction volume from {} to {}", dateFrom, dateTo);
        List<Object[]> results = reportRepository.getHourlyTransactionVolume(dateFrom, dateTo);
        if (results.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "No hourly transaction volume data found for the specified date range",
                                "dateFrom", dateFrom, "dateTo", dateTo));
        }
        List<Map<String, Object>> labeledResults = results.stream()
            .map(row -> Map.of(
                "hour", row[0],
                "transactionCount", row[1]
            ))
            .toList();
        return ResponseEntity.ok(labeledResults);
    }

    @GetMapping("/average-transaction-value")
    public ResponseEntity<Map<String, Object>> getAverageTransactionValue(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo) {
        
        log.info("Generating average transaction value from {} to {}", dateFrom, dateTo);
        Double avgValue = reportRepository.getAverageTransactionValue(dateFrom, dateTo);
        
        Map<String, Object> response = new HashMap<>();
        response.put("averageTransactionValue", avgValue);
        response.put("dateFrom", dateFrom);
        response.put("dateTo", dateTo);
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/gross-vs-net")
    public ResponseEntity<Map<String, Object>> getGrossVsNetAnalysis(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo) {
        
        log.info("Generating gross vs net analysis from {} to {}", dateFrom, dateTo);
        List<Object[]> analysis = reportRepository.getGrossVsNetAnalysis(dateFrom, dateTo);
        
        Map<String, Object> response = new HashMap<>();
        if (!analysis.isEmpty()) {
            Object[] row = analysis.get(0);
            response.put("grossAmount", row[0]);
            response.put("vatAmount", row[1]);
            response.put("serviceFeeAmount", row[2]);
            response.put("netAmount", row[3]);
        }
        response.put("dateFrom", dateFrom);
        response.put("dateTo", dateTo);
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/refund-rate")
    public ResponseEntity<Map<String, Object>> getRefundRate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo) {
        
        log.info("Generating refund rate from {} to {}", dateFrom, dateTo);
        Double refundRate = reportRepository.getRefundRate(dateFrom, dateTo);
        
        Map<String, Object> response = new HashMap<>();
        response.put("refundRate", refundRate);
        response.put("dateFrom", dateFrom);
        response.put("dateTo", dateTo);
        
        return ResponseEntity.ok(response);
    }
}
