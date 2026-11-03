package com.example.postaxvatsystem.controller;

import com.example.postaxvatsystem.dto.FinancialTransactionSummaryDTO;
import com.example.postaxvatsystem.dto.FinancialReportGroupDTO;
import com.example.postaxvatsystem.model.TransactionType;
import com.example.postaxvatsystem.repository.FinancialReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/pos/reports")
@RequiredArgsConstructor
public class FinancialReportController {

    private final FinancialReportRepository financialReportRepository;

    @GetMapping("/financial-audit")
    public ResponseEntity<Map<String, Object>> getFinancialAuditReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo) {
        if (dateFrom.isAfter(dateTo)) {
            return ResponseEntity.badRequest().body(Map.of("message", "dateFrom must be on or before dateTo"));
        }

        List<FinancialReportGroupDTO> groups =
                financialReportRepository.summarizeFinancialActivity(dateFrom, dateTo);
        Map<TransactionType, SummaryTotals> totalsByType = new EnumMap<>(TransactionType.class);
        for (FinancialReportGroupDTO group : groups) {
            totalsByType.computeIfAbsent(group.getTransactionType(), ignored -> new SummaryTotals())
                    .add(group);
        }
        List<FinancialTransactionSummaryDTO> summaries = totalsByType.entrySet().stream()
                .map(entry -> entry.getValue().toDto(entry.getKey()))
                .toList();

        BigDecimal netSales = amount(totalsByType, TransactionType.SALE, SummaryTotals::grossAmount)
                .subtract(adjustmentAmount(groups, TransactionType.SALE, FinancialReportGroupDTO::getGrossAmount));
        BigDecimal netPurchases = amount(totalsByType, TransactionType.PURCHASE, SummaryTotals::grossAmount)
                .subtract(adjustmentAmount(groups, TransactionType.PURCHASE, FinancialReportGroupDTO::getGrossAmount));
        BigDecimal salesVat = amount(totalsByType, TransactionType.SALE, SummaryTotals::vatAmount)
                .subtract(adjustmentAmount(groups, TransactionType.SALE, FinancialReportGroupDTO::getVatAmount));
        BigDecimal purchasesVat = amount(totalsByType, TransactionType.PURCHASE, SummaryTotals::vatAmount)
                .subtract(adjustmentAmount(groups, TransactionType.PURCHASE, FinancialReportGroupDTO::getVatAmount));
        BigDecimal netServiceFees = amount(totalsByType, TransactionType.SALE, SummaryTotals::serviceFeeAmount)
                .subtract(adjustmentAmount(groups, TransactionType.SALE, FinancialReportGroupDTO::getServiceFeeAmount));

        BigDecimal salesAmountDue = amount(totalsByType, TransactionType.SALE, SummaryTotals::netAmount);
        BigDecimal salesAmountPaid = amount(totalsByType, TransactionType.SALE, SummaryTotals::totalAmountPaid);
        BigDecimal purchasesAmountDue = amount(totalsByType, TransactionType.PURCHASE, SummaryTotals::netAmount);
        BigDecimal purchasesAmountPaid = amount(totalsByType, TransactionType.PURCHASE, SummaryTotals::totalAmountPaid);
        BigDecimal refunds = amount(totalsByType, TransactionType.REFUND, SummaryTotals::netAmount);
        BigDecimal reversals = amount(totalsByType, TransactionType.REVERSAL, SummaryTotals::netAmount);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("dateFrom", dateFrom);
        response.put("dateTo", dateTo);
        response.put("transactions", summaries);
        response.put("dateBasis", "SETTLEMENT_DATE_WITH_CREATION_DATE_FALLBACK");
        Map<String, Object> analysis = new LinkedHashMap<>();
        analysis.put("netSalesAfterAdjustments", netSales);
        analysis.put("netPurchasesAfterAdjustments", netPurchases);
        analysis.put("salesVatRecordedAfterAdjustments", salesVat);
        analysis.put("purchasesVatRecordedAfterAdjustments", purchasesVat);
        analysis.put("serviceFeesAfterReversals", netServiceFees);
        analysis.put("refundAmount", refunds);
        analysis.put("reversalAmount", reversals);
        analysis.put("salesAmountDue", salesAmountDue);
        analysis.put("salesAmountPaid", salesAmountPaid);
        analysis.put("salesPaymentVariance", salesAmountPaid.subtract(salesAmountDue));
        analysis.put("purchasesAmountDue", purchasesAmountDue);
        analysis.put("purchasesAmountPaid", purchasesAmountPaid);
        analysis.put("purchasesPaymentVariance", purchasesAmountPaid.subtract(purchasesAmountDue));
        response.put("analysis", analysis);
        response.put("dailyActivity", financialReportRepository.summarizeDailyActivity(dateFrom, dateTo).stream()
                .map(row -> Map.of(
                        "date", row[0].toString(),
                        "transactionType", row[1],
                        "transactionCount", row[2],
                        "netAmount", row[3],
                        "vatAmount", row[4]))
                .toList());
        response.put("paymentMethods", financialReportRepository.summarizePaymentMethods(dateFrom, dateTo).stream()
                .map(row -> Map.of(
                        "paymentMethod", row[0],
                        "tenderCount", row[1],
                        "amountApplied", row[2]))
                .toList());
        response.put("itemActivity", financialReportRepository.summarizeItemActivity(dateFrom, dateTo).stream()
                .map(row -> Map.of(
                        "itemCode", row[0],
                        "itemName", row[1],
                        "saleQuantity", row[2],
                        "purchaseQuantity", row[3],
                        "saleNetAmount", row[4],
                        "purchaseNetAmount", row[5]))
                .toList());
        response.put("operatorActivity", financialReportRepository.summarizeCashierAndRegisterActivity(dateFrom, dateTo).stream()
                .map(row -> Map.of(
                        "cashierId", row[0],
                        "cashRegisterId", row[1],
                        "transactionType", row[2],
                        "transactionCount", row[3],
                        "netAmount", row[4]))
                .toList());
        response.put("outstandingActivity", financialReportRepository.summarizeOutstandingActivity(dateFrom, dateTo).stream()
                .map(row -> Map.of(
                        "transactionType", row[0],
                        "status", row[1],
                        "transactionCount", row[2],
                        "netAmount", row[3],
                        "amountPaid", row[4]))
                .toList());
        return ResponseEntity.ok(response);
    }

    private BigDecimal adjustmentAmount(
            List<FinancialReportGroupDTO> groups,
            TransactionType parentType,
            java.util.function.Function<FinancialReportGroupDTO, BigDecimal> amount) {
        return groups.stream()
                .filter(group -> (group.getTransactionType() == TransactionType.REFUND
                        || group.getTransactionType() == TransactionType.REVERSAL)
                        && group.getParentTransactionType() == parentType)
                .map(amount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal amount(
            Map<TransactionType, SummaryTotals> totals,
            TransactionType type,
            java.util.function.Function<SummaryTotals, BigDecimal> amount) {
        SummaryTotals summary = totals.get(type);
        return summary == null ? BigDecimal.ZERO : amount.apply(summary);
    }

    private static final class SummaryTotals {
        private long transactionCount;
        private BigDecimal grossAmount = BigDecimal.ZERO;
        private BigDecimal vatAmount = BigDecimal.ZERO;
        private BigDecimal serviceFeeAmount = BigDecimal.ZERO;
        private BigDecimal netAmount = BigDecimal.ZERO;
        private BigDecimal totalAmountPaid = BigDecimal.ZERO;

        private void add(FinancialReportGroupDTO group) {
            transactionCount += group.getTransactionCount() == null ? 0 : group.getTransactionCount();
            grossAmount = grossAmount.add(value(group.getGrossAmount()));
            vatAmount = vatAmount.add(value(group.getVatAmount()));
            serviceFeeAmount = serviceFeeAmount.add(value(group.getServiceFeeAmount()));
            netAmount = netAmount.add(value(group.getNetAmount()));
            totalAmountPaid = totalAmountPaid.add(value(group.getTotalAmountPaid()));
        }

        private FinancialTransactionSummaryDTO toDto(TransactionType type) {
            return new FinancialTransactionSummaryDTO(
                    type, transactionCount, grossAmount, vatAmount,
                    serviceFeeAmount, netAmount, totalAmountPaid);
        }

        private static BigDecimal value(BigDecimal amount) {
            return amount == null ? BigDecimal.ZERO : amount;
        }

        private static BigDecimal grossAmount(SummaryTotals totals) {
            return totals.grossAmount;
        }

        private static BigDecimal netAmount(SummaryTotals totals) {
            return totals.netAmount;
        }

        private static BigDecimal vatAmount(SummaryTotals totals) {
            return totals.vatAmount;
        }

        private static BigDecimal serviceFeeAmount(SummaryTotals totals) {
            return totals.serviceFeeAmount;
        }

        private static BigDecimal totalAmountPaid(SummaryTotals totals) {
            return totals.totalAmountPaid;
        }
    }
}
