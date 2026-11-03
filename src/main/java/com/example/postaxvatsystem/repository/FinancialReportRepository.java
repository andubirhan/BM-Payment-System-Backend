package com.example.postaxvatsystem.repository;

import com.example.postaxvatsystem.dto.FinancialReportGroupDTO;
import com.example.postaxvatsystem.model.POSTransaction;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

@org.springframework.stereotype.Repository
public interface FinancialReportRepository extends Repository<POSTransaction, Long> {

    @Query("""
        SELECT new com.example.postaxvatsystem.dto.FinancialReportGroupDTO(
            transaction.transactionType,
            parent.transactionType,
            COUNT(transaction),
            COALESCE(SUM(transaction.grossAmount), 0L),
            COALESCE(SUM(transaction.vatAmount), 0L),
            COALESCE(SUM(transaction.serviceFeeAmount), 0L),
            COALESCE(SUM(transaction.netAmount), 0L),
            COALESCE(SUM(transaction.totalAmountPaid), 0L)
        )
        FROM POSTransaction transaction
        LEFT JOIN POSTransaction parent
            ON parent.transactionId = transaction.parentTransactionId
        WHERE COALESCE(transaction.settledAt, transaction.capturedAt, transaction.createdAt) BETWEEN :dateFrom AND :dateTo
          AND (
              (transaction.transactionType IN (
                  com.example.postaxvatsystem.model.TransactionType.SALE,
                  com.example.postaxvatsystem.model.TransactionType.PURCHASE
              ) AND transaction.status IN (
                  com.example.postaxvatsystem.model.TransactionStatus.COMPLETED,
                  com.example.postaxvatsystem.model.TransactionStatus.SETTLED,
                  com.example.postaxvatsystem.model.TransactionStatus.CAPTURED,
                  com.example.postaxvatsystem.model.TransactionStatus.PARTIALLY_CAPTURED,
                  com.example.postaxvatsystem.model.TransactionStatus.PARTIALLY_REFUNDED,
                  com.example.postaxvatsystem.model.TransactionStatus.REFUNDED,
                  com.example.postaxvatsystem.model.TransactionStatus.REVERSED
              ))
              OR (transaction.transactionType = com.example.postaxvatsystem.model.TransactionType.REFUND
                  AND transaction.status = com.example.postaxvatsystem.model.TransactionStatus.REFUNDED)
              OR (transaction.transactionType = com.example.postaxvatsystem.model.TransactionType.REVERSAL
                  AND transaction.status = com.example.postaxvatsystem.model.TransactionStatus.REVERSED)
          )
        GROUP BY transaction.transactionType
        , parent.transactionType
        ORDER BY transaction.transactionType
        """)
    List<FinancialReportGroupDTO> summarizeFinancialActivity(
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo);

    @Query("""
        SELECT FUNCTION('DATE', COALESCE(transaction.settledAt, transaction.capturedAt, transaction.createdAt)),
               transaction.transactionType,
               COUNT(transaction),
               COALESCE(SUM(transaction.netAmount), 0L),
               COALESCE(SUM(transaction.vatAmount), 0L)
        FROM POSTransaction transaction
        WHERE COALESCE(transaction.settledAt, transaction.capturedAt, transaction.createdAt) BETWEEN :dateFrom AND :dateTo
          AND (
              (transaction.transactionType IN (
                  com.example.postaxvatsystem.model.TransactionType.SALE,
                  com.example.postaxvatsystem.model.TransactionType.PURCHASE
              ) AND transaction.status IN (
                  com.example.postaxvatsystem.model.TransactionStatus.COMPLETED,
                  com.example.postaxvatsystem.model.TransactionStatus.SETTLED,
                  com.example.postaxvatsystem.model.TransactionStatus.CAPTURED,
                  com.example.postaxvatsystem.model.TransactionStatus.PARTIALLY_CAPTURED,
                  com.example.postaxvatsystem.model.TransactionStatus.PARTIALLY_REFUNDED,
                  com.example.postaxvatsystem.model.TransactionStatus.REFUNDED,
                  com.example.postaxvatsystem.model.TransactionStatus.REVERSED
              ))
              OR (transaction.transactionType = com.example.postaxvatsystem.model.TransactionType.REFUND
                  AND transaction.status = com.example.postaxvatsystem.model.TransactionStatus.REFUNDED)
              OR (transaction.transactionType = com.example.postaxvatsystem.model.TransactionType.REVERSAL
                  AND transaction.status = com.example.postaxvatsystem.model.TransactionStatus.REVERSED)
          )
        GROUP BY FUNCTION('DATE', COALESCE(transaction.settledAt, transaction.capturedAt, transaction.createdAt)),
                 transaction.transactionType
        ORDER BY FUNCTION('DATE', COALESCE(transaction.settledAt, transaction.capturedAt, transaction.createdAt)),
                 transaction.transactionType
        """)
    List<Object[]> summarizeDailyActivity(
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo);

    @Query("""
        SELECT tender.paymentMethod, COUNT(tender), COALESCE(SUM(tender.amountApplied), 0L)
        FROM POSPaymentTender tender
        JOIN tender.transaction transaction
        WHERE COALESCE(transaction.settledAt, transaction.capturedAt, transaction.createdAt) BETWEEN :dateFrom AND :dateTo
          AND transaction.transactionType IN (
              com.example.postaxvatsystem.model.TransactionType.SALE,
              com.example.postaxvatsystem.model.TransactionType.PURCHASE
          )
          AND transaction.status IN (
              com.example.postaxvatsystem.model.TransactionStatus.COMPLETED,
              com.example.postaxvatsystem.model.TransactionStatus.SETTLED,
              com.example.postaxvatsystem.model.TransactionStatus.CAPTURED,
              com.example.postaxvatsystem.model.TransactionStatus.PARTIALLY_CAPTURED,
              com.example.postaxvatsystem.model.TransactionStatus.PARTIALLY_REFUNDED,
              com.example.postaxvatsystem.model.TransactionStatus.REFUNDED,
              com.example.postaxvatsystem.model.TransactionStatus.REVERSED
          )
        GROUP BY tender.paymentMethod
        ORDER BY tender.paymentMethod
        """)
    List<Object[]> summarizePaymentMethods(
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo);

    @Query("""
        SELECT line.itemCode, line.itemName,
               COALESCE(SUM(CASE WHEN transaction.transactionType = com.example.postaxvatsystem.model.TransactionType.SALE
                   THEN line.quantity ELSE 0 END), 0L),
               COALESCE(SUM(CASE WHEN transaction.transactionType = com.example.postaxvatsystem.model.TransactionType.PURCHASE
                   THEN line.quantity ELSE 0 END), 0L),
               COALESCE(SUM(CASE WHEN transaction.transactionType = com.example.postaxvatsystem.model.TransactionType.SALE
                   THEN line.lineNetAmount ELSE 0L END), 0L),
               COALESCE(SUM(CASE WHEN transaction.transactionType = com.example.postaxvatsystem.model.TransactionType.PURCHASE
                   THEN line.lineNetAmount ELSE 0L END), 0L)
        FROM POSTransactionLineItem line
        JOIN line.transaction transaction
        WHERE COALESCE(transaction.settledAt, transaction.capturedAt, transaction.createdAt) BETWEEN :dateFrom AND :dateTo
          AND transaction.transactionType IN (
              com.example.postaxvatsystem.model.TransactionType.SALE,
              com.example.postaxvatsystem.model.TransactionType.PURCHASE
          )
          AND transaction.status IN (
              com.example.postaxvatsystem.model.TransactionStatus.COMPLETED,
              com.example.postaxvatsystem.model.TransactionStatus.SETTLED,
              com.example.postaxvatsystem.model.TransactionStatus.CAPTURED,
              com.example.postaxvatsystem.model.TransactionStatus.PARTIALLY_REFUNDED,
              com.example.postaxvatsystem.model.TransactionStatus.REFUNDED,
              com.example.postaxvatsystem.model.TransactionStatus.REVERSED
          )
        GROUP BY line.itemCode, line.itemName
        ORDER BY SUM(CASE WHEN transaction.transactionType = com.example.postaxvatsystem.model.TransactionType.SALE
                     THEN line.quantity ELSE 0 END) DESC
        """)
    List<Object[]> summarizeItemActivity(
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo);

    @Query("""
        SELECT transaction.cashierId, transaction.cashRegisterId, transaction.transactionType,
               COUNT(transaction), COALESCE(SUM(transaction.netAmount), 0L)
        FROM POSTransaction transaction
        WHERE COALESCE(transaction.settledAt, transaction.capturedAt, transaction.createdAt) BETWEEN :dateFrom AND :dateTo
          AND transaction.transactionType IN (
              com.example.postaxvatsystem.model.TransactionType.SALE,
              com.example.postaxvatsystem.model.TransactionType.PURCHASE
          )
          AND transaction.status IN (
              com.example.postaxvatsystem.model.TransactionStatus.COMPLETED,
              com.example.postaxvatsystem.model.TransactionStatus.SETTLED,
              com.example.postaxvatsystem.model.TransactionStatus.CAPTURED,
              com.example.postaxvatsystem.model.TransactionStatus.PARTIALLY_CAPTURED,
              com.example.postaxvatsystem.model.TransactionStatus.PARTIALLY_REFUNDED,
              com.example.postaxvatsystem.model.TransactionStatus.REFUNDED,
              com.example.postaxvatsystem.model.TransactionStatus.REVERSED
          )
        GROUP BY transaction.cashierId, transaction.cashRegisterId, transaction.transactionType
        ORDER BY transaction.cashierId, transaction.cashRegisterId, transaction.transactionType
        """)
    List<Object[]> summarizeCashierAndRegisterActivity(
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo);

    @Query("""
        SELECT transaction.transactionType, transaction.status, COUNT(transaction),
               COALESCE(SUM(transaction.netAmount), 0L),
               COALESCE(SUM(transaction.totalAmountPaid), 0L)
        FROM POSTransaction transaction
        WHERE COALESCE(transaction.settledAt, transaction.capturedAt, transaction.createdAt) BETWEEN :dateFrom AND :dateTo
          AND transaction.status IN (
              com.example.postaxvatsystem.model.TransactionStatus.PENDING,
              com.example.postaxvatsystem.model.TransactionStatus.AUTHORIZED,
              com.example.postaxvatsystem.model.TransactionStatus.PARTIALLY_CAPTURED,
              com.example.postaxvatsystem.model.TransactionStatus.REFUND_PENDING,
              com.example.postaxvatsystem.model.TransactionStatus.REVERSAL_PENDING
          )
        GROUP BY transaction.transactionType, transaction.status
        ORDER BY transaction.transactionType, transaction.status
        """)
    List<Object[]> summarizeOutstandingActivity(
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo);
}
