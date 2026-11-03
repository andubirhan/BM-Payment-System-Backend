package com.example.postaxvatsystem.repository;

import com.example.postaxvatsystem.dto.PendingTransactionDTO;
import com.example.postaxvatsystem.model.POSTransaction;
import com.example.postaxvatsystem.model.TransactionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Main repository for POSTransaction entity.
 * Contains essential CRUD operations and core business queries.
 * For reporting queries, use POSTransactionReportRepository.
 * For search/filter queries, use POSTransactionSearchRepository.
 */
@Repository
public interface POSTransactionRepository extends JpaRepository<POSTransaction, Long> {

    // Essential CRUD queries
    @Query("SELECT t FROM POSTransaction t WHERE UPPER(t.transactionId) = UPPER(:transactionId)")
    Optional<POSTransaction> findByTransactionId(@Param("transactionId") String transactionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM POSTransaction t WHERE UPPER(t.transactionId) = UPPER(:transactionId)")
    Optional<POSTransaction> findByTransactionIdForUpdate(@Param("transactionId") String transactionId);

    @Query("SELECT t FROM POSTransaction t WHERE UPPER(t.idempotencyKey) = UPPER(:idempotencyKey)")
    Optional<POSTransaction> findByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    @Query("SELECT t FROM POSTransaction t WHERE UPPER(t.befaPaymentId) = UPPER(:befaPaymentId)")
    List<POSTransaction> findByBefaPaymentId(@Param("befaPaymentId") String befaPaymentId);

    List<POSTransaction> findByCashRegisterId(String cashRegisterId);

    List<POSTransaction> findByCashierId(String cashierId);

    List<POSTransaction> findByStatus(TransactionStatus status);





    // Core business queries
    @Query("SELECT t FROM POSTransaction t WHERE t.status IN :statuses AND t.preauthExpiresAt <= :now")
    List<POSTransaction> findExpiredPreAuths(@Param("statuses") List<TransactionStatus> statuses, @Param("now") LocalDateTime now);

    @Query("SELECT COALESCE(SUM(t.refundedAmount), 0) FROM POSTransaction t WHERE t.parentTransactionId = :parentTransactionId AND t.transactionType = 'REFUND'")
    BigDecimal sumRefundedAmountByParentTransactionId(@Param("parentTransactionId") String parentTransactionId);

    @Query(value = "SELECT * FROM pos_transactions WHERE status = 'PENDING' AND (CAST(:dateFrom AS timestamp) IS NULL OR created_at >= CAST(:dateFrom AS timestamp)) AND (CAST(:dateTo AS timestamp) IS NULL OR created_at <= CAST(:dateTo AS timestamp)) ORDER BY created_at DESC", nativeQuery = true)
    List<POSTransaction> findPendingTransactions(@Param("dateFrom") LocalDateTime dateFrom, @Param("dateTo") LocalDateTime dateTo);

    @Query(value = "SELECT * FROM pos_transactions WHERE status = 'PENDING' AND (CAST(:dateFrom AS timestamp) IS NULL OR created_at >= CAST(:dateFrom AS timestamp)) AND (CAST(:dateTo AS timestamp) IS NULL OR created_at <= CAST(:dateTo AS timestamp)) ORDER BY created_at DESC", nativeQuery = true)
    Page<POSTransaction> findPendingTransactions(@Param("dateFrom") LocalDateTime dateFrom, @Param("dateTo") LocalDateTime dateTo, Pageable pageable);

    @Query("""
            SELECT new com.example.postaxvatsystem.dto.PendingTransactionDTO(
                t.transactionId, t.customerName, t.transactionType, t.netAmount, t.currencyCode, t.createdAt)
            FROM POSTransaction t
            WHERE t.status = com.example.postaxvatsystem.model.TransactionStatus.PENDING
            ORDER BY t.createdAt DESC
            """)
    Page<PendingTransactionDTO> findPendingTransactionSummaries(Pageable pageable);

    @Query("""
            SELECT new com.example.postaxvatsystem.dto.PendingTransactionDTO(
                t.transactionId, t.customerName, t.transactionType, t.netAmount, t.currencyCode, t.createdAt)
            FROM POSTransaction t
            WHERE t.status = com.example.postaxvatsystem.model.TransactionStatus.PENDING
              AND t.createdAt >= :dateFrom
            ORDER BY t.createdAt DESC
            """)
    Page<PendingTransactionDTO> findPendingTransactionSummariesFrom(
            @Param("dateFrom") LocalDateTime dateFrom,
            Pageable pageable);

    @Query("""
            SELECT new com.example.postaxvatsystem.dto.PendingTransactionDTO(
                t.transactionId, t.customerName, t.transactionType, t.netAmount, t.currencyCode, t.createdAt)
            FROM POSTransaction t
            WHERE t.status = com.example.postaxvatsystem.model.TransactionStatus.PENDING
              AND t.createdAt <= :dateTo
            ORDER BY t.createdAt DESC
            """)
    Page<PendingTransactionDTO> findPendingTransactionSummariesTo(
            @Param("dateTo") LocalDateTime dateTo,
            Pageable pageable);

    @Query("""
            SELECT new com.example.postaxvatsystem.dto.PendingTransactionDTO(
                t.transactionId, t.customerName, t.transactionType, t.netAmount, t.currencyCode, t.createdAt)
            FROM POSTransaction t
            WHERE t.status = com.example.postaxvatsystem.model.TransactionStatus.PENDING
              AND t.createdAt >= :dateFrom
              AND t.createdAt <= :dateTo
            ORDER BY t.createdAt DESC
            """)
    Page<PendingTransactionDTO> findPendingTransactionSummariesBetween(
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            Pageable pageable);
}
