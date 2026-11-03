package com.example.postaxvatsystem.repository;

import com.example.postaxvatsystem.model.POSTransaction;
import com.example.postaxvatsystem.model.PaymentMethod;
import com.example.postaxvatsystem.model.TransactionStatus;
import com.example.postaxvatsystem.model.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for search and filter queries.
 * Contains queries for finding transactions based on various criteria with pagination support.
 */
@Repository
public interface POSTransactionSearchRepository extends JpaRepository<POSTransaction, Long> {

    // All Refunds for a Transaction
    @Query("SELECT t FROM POSTransaction t WHERE t.parentTransactionId = :parentTransactionId AND t.transactionType = 'REFUND'")
    List<POSTransaction> findAllRefundsByParentTransactionId(@Param("parentTransactionId") String parentTransactionId);

    // All Reversals for a Transaction
    @Query("SELECT t FROM POSTransaction t WHERE t.parentTransactionId = :parentTransactionId AND t.transactionType = 'REVERSAL'")
    List<POSTransaction> findAllReversalsByParentTransactionId(@Param("parentTransactionId") String parentTransactionId);

    // All Child Transactions (Refunds + Reversals)
    @Query("SELECT t FROM POSTransaction t WHERE t.parentTransactionId = :parentTransactionId")
    List<POSTransaction> findAllChildTransactions(@Param("parentTransactionId") String parentTransactionId);

    // Customer Transaction History by Name (partial string search, case-insensitive)
    @Query("SELECT t FROM POSTransaction t WHERE LOWER(t.customerName) LIKE LOWER(CONCAT('%', :name, '%')) ORDER BY t.createdAt DESC")
    List<POSTransaction> findByCustomerName(@Param("name") String name);

    // Customer Transaction History by Name with Pagination (partial string search, case-insensitive)
    @Query("SELECT t FROM POSTransaction t WHERE LOWER(t.customerName) LIKE LOWER(CONCAT('%', :name, '%')) ORDER BY t.createdAt DESC")
    Page<POSTransaction> findByCustomerName(@Param("name") String name, Pageable pageable);

    // Customer by TIN (search by hash or plain text for migration, case-insensitive)
    @Query("SELECT t FROM POSTransaction t WHERE t.customerTinHash = :customerTinHash OR LOWER(t.customerTin) = LOWER(:plainTin) ORDER BY t.createdAt DESC")
    List<POSTransaction> findByCustomerTin(@Param("customerTinHash") String customerTinHash, @Param("plainTin") String plainTin);

    // Customer by TIN with Pagination (search by hash or plain text for migration, case-insensitive)
    @Query("SELECT t FROM POSTransaction t WHERE t.customerTinHash = :customerTinHash OR LOWER(t.customerTin) = LOWER(:plainTin) ORDER BY t.createdAt DESC")
    Page<POSTransaction> findByCustomerTin(@Param("customerTinHash") String customerTinHash, @Param("plainTin") String plainTin, Pageable pageable);

    // Advanced Search with Pagination (partial string search for customerName, hash search for TIN)
    @Query(value = """
    SELECT * FROM pos_transactions t WHERE 
    (CAST(:status AS text) IS NULL OR t.status = CAST(:status AS text)) AND 
    (CAST(:transactionType AS text) IS NULL OR t.transaction_type = CAST(:transactionType AS text)) AND 
    (CAST(:cashRegisterId AS text) IS NULL OR t.cash_register_id = CAST(:cashRegisterId AS text)) AND 
    (CAST(:cashierId AS text) IS NULL OR t.cashier_id = CAST(:cashierId AS text)) AND 
    (CAST(:customerName AS text) IS NULL OR LOWER(t.customer_name) LIKE LOWER(CONCAT('%', CAST(:customerName AS text), '%'))) AND 
    (CAST(:befaPaymentId AS text) IS NULL OR LOWER(t.befa_payment_id) LIKE LOWER(CONCAT('%', CAST(:befaPaymentId AS text), '%'))) AND
    (CAST(:bankTransactionId AS text) IS NULL OR LOWER(t.bank_transaction_id) LIKE LOWER(CONCAT('%', CAST(:bankTransactionId AS text), '%'))) AND
    (CAST(:customerTinHash AS text) IS NULL OR t.customer_tin_hash = CAST(:customerTinHash AS text) OR LOWER(t.customer_tin) = LOWER(CAST(:plainCustomerTin AS text))) AND 
    (CAST(:dateFrom AS timestamp) IS NULL OR t.created_at >= CAST(:dateFrom AS timestamp)) AND 
    (CAST(:dateTo AS timestamp) IS NULL OR t.created_at <= CAST(:dateTo AS timestamp)) 
    ORDER BY t.created_at DESC 
    OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
    """,
            countQuery = """
    SELECT COUNT(*) FROM pos_transactions t WHERE 
    (CAST(:status AS text) IS NULL OR t.status = CAST(:status AS text)) AND 
    (CAST(:transactionType AS text) IS NULL OR t.transaction_type = CAST(:transactionType AS text)) AND 
    (CAST(:cashRegisterId AS text) IS NULL OR t.cash_register_id = CAST(:cashRegisterId AS text)) AND 
    (CAST(:cashierId AS text) IS NULL OR t.cashier_id = CAST(:cashierId AS text)) AND 
    (CAST(:customerName AS text) IS NULL OR LOWER(t.customer_name) LIKE LOWER(CONCAT('%', CAST(:customerName AS text), '%'))) AND 
    (CAST(:befaPaymentId AS text) IS NULL OR LOWER(t.befa_payment_id) LIKE LOWER(CONCAT('%', CAST(:befaPaymentId AS text), '%'))) AND
    (CAST(:bankTransactionId AS text) IS NULL OR LOWER(t.bank_transaction_id) LIKE LOWER(CONCAT('%', CAST(:bankTransactionId AS text), '%'))) AND
    (CAST(:customerTinHash AS text) IS NULL OR t.customer_tin_hash = CAST(:customerTinHash AS text) OR LOWER(t.customer_tin) = LOWER(CAST(:plainCustomerTin AS text))) AND 
    (CAST(:dateFrom AS timestamp) IS NULL OR t.created_at >= CAST(:dateFrom AS timestamp)) AND 
    (CAST(:dateTo AS timestamp) IS NULL OR t.created_at <= CAST(:dateTo AS timestamp))
    """,
            nativeQuery = true)
    List<POSTransaction> searchTransactionsNative(
            @Param("status") String status,
            @Param("transactionType") String transactionType,
            @Param("cashRegisterId") String cashRegisterId,
            @Param("cashierId") String cashierId,
            @Param("customerName") String customerName,
            @Param("befaPaymentId") String befaPaymentId,
            @Param("bankTransactionId") String bankTransactionId,
            @Param("customerTinHash") String customerTinHash,
            @Param("plainCustomerTin") String plainCustomerTin,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    // Count query for pagination (partial string search for customerName, hash search for TIN)
    @Query(value = """
    SELECT COUNT(*) FROM pos_transactions t WHERE 
    (CAST(:status AS text) IS NULL OR t.status = CAST(:status AS text)) AND 
    (CAST(:transactionType AS text) IS NULL OR t.transaction_type = CAST(:transactionType AS text)) AND 
    (CAST(:cashRegisterId AS text) IS NULL OR t.cash_register_id = CAST(:cashRegisterId AS text)) AND 
    (CAST(:cashierId AS text) IS NULL OR t.cashier_id = CAST(:cashierId AS text)) AND 
    (CAST(:customerName AS text) IS NULL OR LOWER(t.customer_name) LIKE LOWER(CONCAT('%', CAST(:customerName AS text), '%'))) AND 
    (CAST(:befaPaymentId AS text) IS NULL OR LOWER(t.befa_payment_id) LIKE LOWER(CONCAT('%', CAST(:befaPaymentId AS text), '%'))) AND
    (CAST(:bankTransactionId AS text) IS NULL OR LOWER(t.bank_transaction_id) LIKE LOWER(CONCAT('%', CAST(:bankTransactionId AS text), '%'))) AND
    (CAST(:customerTinHash AS text) IS NULL OR t.customer_tin_hash = CAST(:customerTinHash AS text) OR LOWER(t.customer_tin) = LOWER(CAST(:plainCustomerTin AS text))) AND 
    (CAST(:dateFrom AS timestamp) IS NULL OR t.created_at >= CAST(:dateFrom AS timestamp)) AND 
    (CAST(:dateTo AS timestamp) IS NULL OR t.created_at <= CAST(:dateTo AS timestamp))
    """,
            nativeQuery = true)
    Long countSearchTransactionsNative(
            @Param("status") String status,
            @Param("transactionType") String transactionType,
            @Param("cashRegisterId") String cashRegisterId,
            @Param("cashierId") String cashierId,
            @Param("customerName") String customerName,
            @Param("befaPaymentId") String befaPaymentId,
            @Param("bankTransactionId") String bankTransactionId,
            @Param("customerTinHash") String customerTinHash,
            @Param("plainCustomerTin") String plainCustomerTin,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo
    );

    // Advanced Search without Pagination (for small result sets, exact match for encrypted fields)
    @Query("SELECT t FROM POSTransaction t WHERE " +
           "(:status IS NULL OR t.status = :status) AND " +
           "(:transactionType IS NULL OR t.transactionType = :transactionType) AND " +
           "(:cashRegisterId IS NULL OR t.cashRegisterId = :cashRegisterId) AND " +
           "(:cashierId IS NULL OR t.cashierId = :cashierId) AND " +
           "(:customerName IS NULL OR t.customerName = :customerName) AND " +
           "(:customerTin IS NULL OR t.customerTin = :customerTin) AND " +
           "(:dateFrom IS NULL OR t.createdAt >= :dateFrom) AND " +
           "(:dateTo IS NULL OR t.createdAt <= :dateTo)")
    List<POSTransaction> searchTransactions(
            @Param("status") TransactionStatus status,
            @Param("transactionType") TransactionType transactionType,
            @Param("cashRegisterId") String cashRegisterId,
            @Param("cashierId") String cashierId,
            @Param("customerName") String customerName,
            @Param("customerTin") String customerTin,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo
    );

    // Full-Text Search with Pagination (excludes encrypted fields)
    @Query("SELECT t FROM POSTransaction t WHERE " +
           "(t.transactionId ILIKE CONCAT('%', :query, '%') OR " +
           "t.fiscalReference ILIKE CONCAT('%', :query, '%') OR " +
           "t.customerName ILIKE CONCAT('%', :query, '%'))")
    Page<POSTransaction> fullTextSearch(@Param("query") String query, Pageable pageable);

    // Full-Text Search without Pagination (excludes encrypted fields)
    @Query("SELECT t FROM POSTransaction t WHERE " +
           "(t.transactionId ILIKE CONCAT('%', :query, '%') OR " +
           "t.fiscalReference ILIKE CONCAT('%', :query, '%') OR " +
           "t.customerName ILIKE CONCAT('%', :query, '%'))")
    List<POSTransaction> fullTextSearch(@Param("query") String query);

    // Search by Status and Date Range with Pagination
    @Query("SELECT t FROM POSTransaction t WHERE t.status = :status AND t.createdAt BETWEEN :dateFrom AND :dateTo ORDER BY t.createdAt DESC")
    Page<POSTransaction> findByStatusAndDateRange(@Param("status") TransactionStatus status, @Param("dateFrom") LocalDateTime dateFrom, @Param("dateTo") LocalDateTime dateTo, Pageable pageable);

    // Search by Transaction Type and Date Range with Pagination
    @Query("SELECT t FROM POSTransaction t WHERE t.transactionType = :transactionType AND t.createdAt BETWEEN :dateFrom AND :dateTo ORDER BY t.createdAt DESC")
    Page<POSTransaction> findByTransactionTypeAndDateRange(@Param("transactionType") TransactionType transactionType, @Param("dateFrom") LocalDateTime dateFrom, @Param("dateTo") LocalDateTime dateTo, Pageable pageable);

    // Search by Cashier and Date Range with Pagination
    @Query("SELECT t FROM POSTransaction t WHERE t.cashierId = :cashierId AND t.createdAt BETWEEN :dateFrom AND :dateTo ORDER BY t.createdAt DESC")
    Page<POSTransaction> findByCashierIdAndDateRange(@Param("cashierId") String cashierId, @Param("dateFrom") LocalDateTime dateFrom, @Param("dateTo") LocalDateTime dateTo, Pageable pageable);

    // Search by Cash Register and Date Range with Pagination
    @Query("SELECT t FROM POSTransaction t WHERE t.cashRegisterId = :cashRegisterId AND t.createdAt BETWEEN :dateFrom AND :dateTo ORDER BY t.createdAt DESC")
    Page<POSTransaction> findByCashRegisterIdAndDateRange(@Param("cashRegisterId") String cashRegisterId, @Param("dateFrom") LocalDateTime dateFrom, @Param("dateTo") LocalDateTime dateTo, Pageable pageable);

    // Search by Amount Range with Pagination
    @Query("SELECT t FROM POSTransaction t WHERE t.netAmount BETWEEN :minAmount AND :maxAmount ORDER BY t.createdAt DESC")
    Page<POSTransaction> findByNetAmountRange(@Param("minAmount") Long minAmount, @Param("maxAmount") Long maxAmount, Pageable pageable);

    // Search by Amount Range and Status with Pagination
    @Query("SELECT t FROM POSTransaction t WHERE t.netAmount BETWEEN :minAmount AND :maxAmount AND t.status = :status ORDER BY t.createdAt DESC")
    Page<POSTransaction> findByNetAmountRangeAndStatus(@Param("minAmount") Long minAmount, @Param("maxAmount") Long maxAmount, @Param("status") TransactionStatus status, Pageable pageable);

    // Search by Cancellation Reason
    @Query("SELECT t FROM POSTransaction t WHERE t.cancellationReason IS NOT NULL AND t.cancellationReason ILIKE CONCAT('%', :reason, '%') ORDER BY t.createdAt DESC")
    List<POSTransaction> findByCancellationReason(@Param("reason") String reason);

    // Search by Cancellation Reason with Pagination
    @Query("SELECT t FROM POSTransaction t WHERE t.cancellationReason IS NOT NULL AND t.cancellationReason ILIKE CONCAT('%', :reason, '%') ORDER BY t.createdAt DESC")
    Page<POSTransaction> findByCancellationReason(@Param("reason") String reason, Pageable pageable);

    // Search by Multiple Transaction IDs
    @Query("SELECT t FROM POSTransaction t WHERE t.transactionId IN :transactionIds")
    List<POSTransaction> findByTransactionIds(@Param("transactionIds") List<String> transactionIds);

    // Search by Parent Transaction ID (find all related transactions)
    @Query("SELECT t FROM POSTransaction t WHERE t.parentTransactionId = :parentTransactionId OR t.transactionId = :parentTransactionId")
    List<POSTransaction> findTransactionHierarchy(@Param("parentTransactionId") String parentTransactionId);

    // Search by Authorization Code (via payment tender)
    @Query("SELECT t FROM POSTransaction t JOIN t.paymentTenders pt WHERE pt.authCode = :authCode")
    Optional<POSTransaction> findByAuthorizationCode(@Param("authCode") String authCode);

    // Search by Retrieval Reference Number (via payment tender)
    @Query("SELECT t FROM POSTransaction t JOIN t.paymentTenders pt WHERE pt.rrn = :rrn")
    Optional<POSTransaction> findByRetrievalReferenceNumber(@Param("rrn") String rrn);

    // Search by Card Last Four Digits (via payment tender)
    @Query("SELECT t FROM POSTransaction t JOIN t.paymentTenders pt WHERE pt.cardLastFour = :cardLastFour ORDER BY t.createdAt DESC")
    List<POSTransaction> findByCardLastFour(@Param("cardLastFour") String cardLastFour);

    // Search by Card Last Four Digits with Pagination
    @Query("SELECT t FROM POSTransaction t JOIN t.paymentTenders pt WHERE pt.cardLastFour = :cardLastFour ORDER BY t.createdAt DESC")
    Page<POSTransaction> findByCardLastFour(@Param("cardLastFour") String cardLastFour, Pageable pageable);

    // Search by Payment Method
    @Query("SELECT t FROM POSTransaction t JOIN t.paymentTenders pt WHERE pt.paymentMethod = :paymentMethod ORDER BY t.createdAt DESC")
    List<POSTransaction> findByPaymentMethod(@Param("paymentMethod") String paymentMethod);

    // Search by Payment Method with Pagination
    @Query("SELECT t FROM POSTransaction t JOIN t.paymentTenders pt WHERE pt.paymentMethod = :paymentMethod ORDER BY t.createdAt DESC")
    Page<POSTransaction> findByPaymentMethod(@Param("paymentMethod") PaymentMethod paymentMethod, Pageable pageable);

    // Search by Payment Method and Date Range
    @Query("SELECT t FROM POSTransaction t JOIN t.paymentTenders pt WHERE pt.paymentMethod = :paymentMethod AND t.createdAt BETWEEN :dateFrom AND :dateTo ORDER BY t.createdAt DESC")
    Page<POSTransaction> findByPaymentMethodAndDateRange(@Param("paymentMethod") String paymentMethod, @Param("dateFrom") LocalDateTime dateFrom, @Param("dateTo") LocalDateTime dateTo, Pageable pageable);

    // Search by VAT Category (via line items)
    @Query("SELECT DISTINCT t FROM POSTransaction t JOIN t.lineItems li WHERE li.vatCategory = :vatCategory ORDER BY t.createdAt DESC")
    List<POSTransaction> findByVatCategory(@Param("vatCategory") String vatCategory);

    // Search by VAT Category with Pagination
    @Query("SELECT DISTINCT t FROM POSTransaction t JOIN t.lineItems li WHERE li.vatCategory = :vatCategory ORDER BY t.createdAt DESC")
    Page<POSTransaction> findByVatCategory(@Param("vatCategory") String vatCategory, Pageable pageable);

    // Search by Item Code (via line items)
    @Query("SELECT DISTINCT t FROM POSTransaction t JOIN t.lineItems li WHERE li.itemCode = :itemCode ORDER BY t.createdAt DESC")
    List<POSTransaction> findByItemCode(@Param("itemCode") String itemCode);

    // Search by Item Code with Pagination
    @Query("SELECT DISTINCT t FROM POSTransaction t JOIN t.lineItems li WHERE li.itemCode = :itemCode ORDER BY t.createdAt DESC")
    Page<POSTransaction> findByItemCode(@Param("itemCode") String itemCode, Pageable pageable);

    // Search by Item Name (via line items)
    @Query("SELECT DISTINCT t FROM POSTransaction t JOIN t.lineItems li WHERE li.itemName ILIKE CONCAT('%', :itemName, '%') ORDER BY t.createdAt DESC")
    List<POSTransaction> findByItemName(@Param("itemName") String itemName);

    // Search by Item Name with Pagination
    @Query("SELECT DISTINCT t FROM POSTransaction t JOIN t.lineItems li WHERE li.itemName ILIKE CONCAT('%', :itemName, '%') ORDER BY t.createdAt DESC")
    Page<POSTransaction> findByItemName(@Param("itemName") String itemName, Pageable pageable);

    // Recent Transactions (last N transactions)
    @Query("SELECT t FROM POSTransaction t ORDER BY t.createdAt DESC")
    Page<POSTransaction> findRecentTransactions(Pageable pageable);

    // Recent Transactions by Cashier
    @Query("SELECT t FROM POSTransaction t WHERE t.cashierId = :cashierId ORDER BY t.createdAt DESC")
    Page<POSTransaction> findRecentTransactionsByCashier(@Param("cashierId") String cashierId, Pageable pageable);

    // Recent Transactions by Register
    @Query("SELECT t FROM POSTransaction t WHERE t.cashRegisterId = :cashRegisterId ORDER BY t.createdAt DESC")
    Page<POSTransaction> findRecentTransactionsByRegister(@Param("cashRegisterId") String cashRegisterId, Pageable pageable);

    /**
     * Finds transactions that got stuck in PENDING because of a system crash
     * or network drop during the API response phase.
     * Used by the PaymentReconciliationScheduler to recover stranded transactions.
     */
    @Query("SELECT t FROM POSTransaction t WHERE t.status = :status AND t.createdAt <= :threshold")
    List<POSTransaction> findStrandedPendingTransactions(
            @Param("status") TransactionStatus status,
            @Param("threshold") LocalDateTime threshold
    );

    /**
     * Finds transactions by BEFA payment ID.
     * Used for safe retries - clients can check if a payment already exists before initiating.
     * Returns a list since multiple transactions may exist for the same BEFA payment ID.
     */
    List<POSTransaction> findByBefaPaymentId(String befaPaymentId);

    /**
     * Finds transactions by bank transaction ID.
     * Used for retrieving transactions by the bank's reference.
     */
    Optional<POSTransaction> findByBankTransactionId(String bankTransactionId);

    /**
     * Unified search by any of the three primary identifiers.
     * Searches by transactionId, befaPaymentId, or bankTransactionId.
     * Returns the most recent match if multiple exist.
     */
    @Query("SELECT t FROM POSTransaction t WHERE " +
           "t.transactionId = :identifier OR " +
           "t.befaPaymentId = :identifier OR " +
           "t.bankTransactionId = :identifier " +
           "ORDER BY t.createdAt DESC")
    Optional<POSTransaction> findByAnyIdentifier(@Param("identifier") String identifier);

    /**
     * Finds all transactions (including refunds/reversals) for a given identifier.
     * Useful for getting complete transaction history by any ID.
     */
    @Query("SELECT t FROM POSTransaction t WHERE " +
           "t.transactionId = :identifier OR " +
           "t.befaPaymentId = :identifier OR " +
           "t.bankTransactionId = :identifier OR " +
           "t.parentTransactionId = :identifier " +
           "ORDER BY t.createdAt DESC")
    List<POSTransaction> findAllByAnyIdentifier(@Param("identifier") String identifier);

    /**
     * Finds capture transactions for a pre-auth transaction.
     * Capture transactions are stored as separate records with the same parentTransactionId.
     */
    @Query("SELECT t FROM POSTransaction t WHERE t.parentTransactionId = :preAuthTransactionId AND t.transactionType = 'PURCHASE'")
    List<POSTransaction> findCapturesByPreAuthId(@Param("preAuthTransactionId") String preAuthTransactionId);
}
