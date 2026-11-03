package com.example.postaxvatsystem.repository;

import com.example.postaxvatsystem.model.POSTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for reporting and aggregation queries.
 * Contains queries for revenue, VAT, performance metrics, and summaries.
 */
@Repository
public interface POSTransactionReportRepository extends JpaRepository<POSTransaction, Long> {

    // Revenue Reporting
    @Query("""
            SELECT
              COALESCE(SUM(CASE WHEN t.status = 'COMPLETED' THEN t.netAmount ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN t.status = 'COMPLETED' THEN t.vatAmount ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN t.status = 'COMPLETED' THEN t.serviceFeeAmount ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN t.transactionType = 'REFUND' AND t.status = 'REFUNDED' THEN t.netAmount ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN t.status = 'COMPLETED' THEN t.grossAmount ELSE 0 END), 0)
            FROM POSTransaction t
            WHERE t.createdAt BETWEEN :dateFrom AND :dateTo
              AND (t.status = 'COMPLETED' OR (t.transactionType = 'REFUND' AND t.status = 'REFUNDED'))
            """)
    Object[] calculateRevenueTotalsByDateRange(
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo);

    @Query("SELECT COALESCE(SUM(t.netAmount), 0) FROM POSTransaction t WHERE t.status = 'COMPLETED' AND t.createdAt BETWEEN :dateFrom AND :dateTo")
    Long calculateRevenueByDateRange(@Param("dateFrom") LocalDateTime dateFrom, @Param("dateTo") LocalDateTime dateTo);

    // VAT Reporting
    @Query("SELECT COALESCE(SUM(t.vatAmount), 0) FROM POSTransaction t WHERE t.status = 'COMPLETED' AND t.createdAt BETWEEN :dateFrom AND :dateTo")
    Long calculateVATByDateRange(@Param("dateFrom") LocalDateTime dateFrom, @Param("dateTo") LocalDateTime dateTo);

    // Service Fee Reporting
    @Query("SELECT COALESCE(SUM(t.serviceFeeAmount), 0) FROM POSTransaction t WHERE t.status = 'COMPLETED' AND t.createdAt BETWEEN :dateFrom AND :dateTo")
    Long calculateServiceFeesByDateRange(@Param("dateFrom") LocalDateTime dateFrom, @Param("dateTo") LocalDateTime dateTo);

    // Total Refund Amount by Date
    @Query("SELECT COALESCE(SUM(t.netAmount), 0) FROM POSTransaction t WHERE t.transactionType = 'REFUND' AND t.status = 'REFUNDED' AND t.createdAt BETWEEN :dateFrom AND :dateTo")
    Long calculateTotalRefundsByDateRange(@Param("dateFrom") LocalDateTime dateFrom, @Param("dateTo") LocalDateTime dateTo);

    // Cashier Performance with Totals
    @Query("SELECT t.cashierId, COUNT(t), COALESCE(SUM(t.netAmount), 0) FROM POSTransaction t WHERE t.createdAt BETWEEN :dateFrom AND :dateTo GROUP BY t.cashierId")
    List<Object[]> getCashierPerformance(@Param("dateFrom") LocalDateTime dateFrom, @Param("dateTo") LocalDateTime dateTo);

    // Register Reconciliation
    @Query("SELECT t FROM POSTransaction t WHERE t.cashRegisterId = :cashRegisterId AND t.createdAt BETWEEN :dateFrom AND :dateTo ORDER BY t.createdAt DESC")
    List<POSTransaction> findByCashRegisterIdAndDateRange(@Param("cashRegisterId") String cashRegisterId, @Param("dateFrom") LocalDateTime dateFrom, @Param("dateTo") LocalDateTime dateTo);

    // Failed Transactions
    @Query("SELECT t FROM POSTransaction t WHERE t.status = 'FAILED' AND t.createdAt BETWEEN :dateFrom AND :dateTo")
    List<POSTransaction> findFailedTransactions(@Param("dateFrom") LocalDateTime dateFrom, @Param("dateTo") LocalDateTime dateTo);

    // Settled vs Unsettled Transactions
    @Query("SELECT t FROM POSTransaction t WHERE t.settledAt IS NULL AND t.createdAt < :cutoffDate")
    List<POSTransaction> findUnsettledTransactions(@Param("cutoffDate") LocalDateTime cutoffDate);

    // High-Value Transactions
    @Query("SELECT t FROM POSTransaction t WHERE t.netAmount >= :threshold ORDER BY t.netAmount DESC")
    List<POSTransaction> findHighValueTransactions(@Param("threshold") Long threshold);

    // Payment Method Breakdown
    @Query("SELECT pt.paymentMethod, COUNT(pt), COALESCE(SUM(pt.amountApplied), 0) FROM POSPaymentTender pt JOIN pt.transaction t WHERE t.createdAt BETWEEN :dateFrom AND :dateTo GROUP BY pt.paymentMethod")
    List<Object[]> getPaymentMethodBreakdown(@Param("dateFrom") LocalDateTime dateFrom, @Param("dateTo") LocalDateTime dateTo);

    // Daily Summary
    @Query("SELECT DATE(t.createdAt), COUNT(t), COALESCE(SUM(t.netAmount), 0), COALESCE(SUM(t.vatAmount), 0) FROM POSTransaction t WHERE t.status = 'COMPLETED' AND t.createdAt BETWEEN :dateFrom AND :dateTo GROUP BY DATE(t.createdAt)")
    List<Object[]> getDailySummary(@Param("dateFrom") LocalDateTime dateFrom, @Param("dateTo") LocalDateTime dateTo);

    // Pre-Auths About to Expire
    @Query("SELECT t FROM POSTransaction t WHERE t.status = 'AUTHORIZED' AND t.preauthExpiresAt BETWEEN :now AND :expiryWindow")
    List<POSTransaction> findPreAuthsAboutToExpire(@Param("now") LocalDateTime now, @Param("expiryWindow") LocalDateTime expiryWindow);

    // Transaction Count by Status
    @Query("SELECT t.status, COUNT(t) FROM POSTransaction t WHERE t.createdAt BETWEEN :dateFrom AND :dateTo GROUP BY t.status")
    List<Object[]> getTransactionCountByStatus(@Param("dateFrom") LocalDateTime dateFrom, @Param("dateTo") LocalDateTime dateTo);

    // Transaction Count by Type
    @Query("SELECT t.transactionType, COUNT(t) FROM POSTransaction t WHERE t.createdAt BETWEEN :dateFrom AND :dateTo GROUP BY t.transactionType")
    List<Object[]> getTransactionCountByType(@Param("dateFrom") LocalDateTime dateFrom, @Param("dateTo") LocalDateTime dateTo);

    // Hourly Transaction Volume
    @Query("SELECT EXTRACT(HOUR FROM t.createdAt), COUNT(t) FROM POSTransaction t WHERE t.createdAt BETWEEN :dateFrom AND :dateTo GROUP BY EXTRACT(HOUR FROM t.createdAt) ORDER BY EXTRACT(HOUR FROM t.createdAt)")
    List<Object[]> getHourlyTransactionVolume(@Param("dateFrom") LocalDateTime dateFrom, @Param("dateTo") LocalDateTime dateTo);

    // Monthly Summary
    @Query("SELECT EXTRACT(MONTH FROM t.createdAt), EXTRACT(YEAR FROM t.createdAt), COUNT(t), COALESCE(SUM(t.netAmount), 0), COALESCE(SUM(t.vatAmount), 0) FROM POSTransaction t WHERE t.status = 'COMPLETED' AND t.createdAt BETWEEN :dateFrom AND :dateTo GROUP BY EXTRACT(MONTH FROM t.createdAt), EXTRACT(YEAR FROM t.createdAt) ORDER BY EXTRACT(YEAR FROM t.createdAt), EXTRACT(MONTH FROM t.createdAt)")
    List<Object[]> getMonthlySummary(@Param("dateFrom") LocalDateTime dateFrom, @Param("dateTo") LocalDateTime dateTo);

    // Average Transaction Value
    @Query("SELECT COALESCE(AVG(t.netAmount), 0) FROM POSTransaction t WHERE t.status = 'COMPLETED' AND t.createdAt BETWEEN :dateFrom AND :dateTo")
    Double getAverageTransactionValue(@Param("dateFrom") LocalDateTime dateFrom, @Param("dateTo") LocalDateTime dateTo);

    // Gross vs Net Analysis
    @Query("SELECT COALESCE(SUM(t.grossAmount), 0), COALESCE(SUM(t.vatAmount), 0), COALESCE(SUM(t.serviceFeeAmount), 0), COALESCE(SUM(t.netAmount), 0) FROM POSTransaction t WHERE t.status = 'COMPLETED' AND t.createdAt BETWEEN :dateFrom AND :dateTo")
    List<Object[]> getGrossVsNetAnalysis(@Param("dateFrom") LocalDateTime dateFrom, @Param("dateTo") LocalDateTime dateTo);

    // Refund Rate
    @Query("SELECT CAST(COUNT(t) AS double) / NULLIF((SELECT COUNT(t2) FROM POSTransaction t2 WHERE t2.status = 'COMPLETED' AND t2.createdAt BETWEEN :dateFrom AND :dateTo), 0) FROM POSTransaction t WHERE t.transactionType = 'REFUND' AND t.status = 'REFUNDED' AND t.createdAt BETWEEN :dateFrom AND :dateTo")
    Double getRefundRate(@Param("dateFrom") LocalDateTime dateFrom, @Param("dateTo") LocalDateTime dateTo);

    // Top Cashiers by Revenue
    @Query("SELECT t.cashierId, COUNT(t), COALESCE(SUM(t.netAmount), 0) FROM POSTransaction t WHERE t.status = 'COMPLETED' AND t.createdAt BETWEEN :dateFrom AND :dateTo GROUP BY t.cashierId ORDER BY SUM(t.netAmount) DESC")
    List<Object[]> getTopCashiersByRevenue(@Param("dateFrom") LocalDateTime dateFrom, @Param("dateTo") LocalDateTime dateTo);

    // Top Registers by Revenue
    @Query("SELECT t.cashRegisterId, COUNT(t), COALESCE(SUM(t.netAmount), 0) FROM POSTransaction t WHERE t.status = 'COMPLETED' AND t.createdAt BETWEEN :dateFrom AND :dateTo GROUP BY t.cashRegisterId ORDER BY SUM(t.netAmount) DESC")
    List<Object[]> getTopRegistersByRevenue(@Param("dateFrom") LocalDateTime dateFrom, @Param("dateTo") LocalDateTime dateTo);

    // Total Transactions Count
    @Query("SELECT COUNT(t) FROM POSTransaction t WHERE t.createdAt BETWEEN :dateFrom AND :dateTo")
    Long getTotalTransactionsCount(@Param("dateFrom") LocalDateTime dateFrom, @Param("dateTo") LocalDateTime dateTo);

    // Completed Transactions Count
    @Query("SELECT COUNT(t) FROM POSTransaction t WHERE t.status = 'COMPLETED' AND t.createdAt BETWEEN :dateFrom AND :dateTo")
    Long getCompletedTransactionsCount(@Param("dateFrom") LocalDateTime dateFrom, @Param("dateTo") LocalDateTime dateTo);

    // Refunded Transactions Count
    @Query("SELECT COUNT(t) FROM POSTransaction t WHERE t.status = 'REFUNDED' AND t.createdAt BETWEEN :dateFrom AND :dateTo")
    Long getRefundedTransactionsCount(@Param("dateFrom") LocalDateTime dateFrom, @Param("dateTo") LocalDateTime dateTo);

    // Reversed Transactions Count
    @Query("SELECT COUNT(t) FROM POSTransaction t WHERE t.status = 'REVERSED' AND t.createdAt BETWEEN :dateFrom AND :dateTo")
    Long getReversedTransactionsCount(@Param("dateFrom") LocalDateTime dateFrom, @Param("dateTo") LocalDateTime dateTo);

    // Transactions by Fiscal Reference
    @Query("SELECT t FROM POSTransaction t WHERE t.fiscalReference = :fiscalReference")
    Optional<POSTransaction> findByFiscalReference(@Param("fiscalReference") String fiscalReference);
    /** One aggregate query for the dashboard’s current and comparison periods. */
    @Query(value = """
            SELECT
              COALESCE(SUM(CASE WHEN status = 'COMPLETED' AND created_at BETWEEN :dateFrom AND :dateTo THEN net_amount ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN status = 'COMPLETED' AND created_at BETWEEN :dateFrom AND :dateTo THEN vat_amount ELSE 0 END), 0),
              COALESCE(COUNT(CASE WHEN status = 'COMPLETED' AND created_at BETWEEN :dateFrom AND :dateTo THEN 1 END), 0),
              COALESCE(AVG(CASE WHEN status = 'COMPLETED' AND created_at BETWEEN :dateFrom AND :dateTo THEN net_amount END), 0),
              COALESCE(SUM(CASE WHEN status = 'COMPLETED' AND created_at BETWEEN :previousDateFrom AND :previousDateTo THEN net_amount ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN status = 'COMPLETED' AND created_at BETWEEN :previousDateFrom AND :previousDateTo THEN vat_amount ELSE 0 END), 0),
              COALESCE(COUNT(CASE WHEN status = 'COMPLETED' AND created_at BETWEEN :previousDateFrom AND :previousDateTo THEN 1 END), 0),
              COALESCE(AVG(CASE WHEN status = 'COMPLETED' AND created_at BETWEEN :previousDateFrom AND :previousDateTo THEN net_amount END), 0),
              COALESCE(COUNT(CASE WHEN status = 'PENDING' THEN 1 END), 0),
              COALESCE(SUM(CASE WHEN transaction_type = 'SALE' AND status IN ('COMPLETED', 'SETTLED') AND created_at BETWEEN :dateFrom AND :dateTo THEN net_amount ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN transaction_type = 'PURCHASE' AND status IN ('COMPLETED', 'SETTLED') AND created_at BETWEEN :dateFrom AND :dateTo THEN net_amount ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN transaction_type = 'SALE' AND status IN ('COMPLETED', 'SETTLED') AND created_at BETWEEN :previousDateFrom AND :previousDateTo THEN net_amount ELSE 0 END), 0),
              COALESCE(SUM(CASE WHEN transaction_type = 'PURCHASE' AND status IN ('COMPLETED', 'SETTLED') AND created_at BETWEEN :previousDateFrom AND :previousDateTo THEN net_amount ELSE 0 END), 0)
            FROM pos_transactions
            """, nativeQuery = true)
    Object getDashboardSummary(
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            @Param("previousDateFrom") LocalDateTime previousDateFrom,
            @Param("previousDateTo") LocalDateTime previousDateTo);
}
