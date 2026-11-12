package com.example.postaxvatsystem.controller;

import com.example.postaxvatsystem.model.POSTransaction;
import com.example.postaxvatsystem.model.PaymentMethod;
import com.example.postaxvatsystem.model.TransactionStatus;
import com.example.postaxvatsystem.model.TransactionType;
import com.example.postaxvatsystem.repository.POSTransactionSearchRepository;
import com.example.postaxvatsystem.security.EncryptionService;
import com.example.postaxvatsystem.security.HashService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/pos/search")
@RequiredArgsConstructor
@Slf4j
public class POSTransactionSearchController {

    private final POSTransactionSearchRepository searchRepository;
    private final EncryptionService encryptionService;
    private final HashService hashService;

    @GetMapping("/transactions")
    public ResponseEntity<?> searchTransactions(
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(defaultValue = "false") boolean excludePending,
            @RequestParam(required = false) TransactionType transactionType,
            @RequestParam(required = false) TransactionType businessTransactionType,
            @RequestParam(required = false) String cashRegisterId,
            @RequestParam(required = false) String cashierId,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String befaPaymentId,
            @RequestParam(required = false) String bankTransactionId,
            @RequestParam(required = false) String transactionId,
            @RequestParam(required = false) String customerTin,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size) {
        try {
            // Parse page and size with validation
            int pageNum = 0;
            int sizeNum = 20;
            try {
                if (page != null && !page.isEmpty() && !page.equals("[objectObject]")) {
                    pageNum = Integer.parseInt(page);
                }
            } catch (NumberFormatException e) {
                log.warn("Invalid page parameter '{}', using default 0", page);
            }
            try {
                if (size != null && !size.isEmpty() && !size.equals("[objectObject]")) {
                    sizeNum = Integer.parseInt(size);
                }
            } catch (NumberFormatException e) {
                log.warn("Invalid size parameter '{}', using default 20", size);
            }

            int offset = pageNum * sizeNum;
            String statusStr = status != null ? status.name() : null;
            String transactionTypeStr = transactionType != null ? transactionType.name() : null;
            String businessTransactionTypeStr = businessTransactionType != null
                    ? businessTransactionType.name() : null;
            
            // Treat empty strings as null for optional parameters
            String cashRegisterIdStr = (cashRegisterId != null && !cashRegisterId.isEmpty()) ? cashRegisterId : null;
            String cashierIdStr = (cashierId != null && !cashierId.isEmpty()) ? cashierId : null;
            String befaPaymentIdStr = (befaPaymentId != null && !befaPaymentId.isBlank()) ? befaPaymentId.trim() : null;
            String bankTransactionIdStr = (bankTransactionId != null && !bankTransactionId.isBlank()) ? bankTransactionId.trim() : null;
            String transactionIdStr = (transactionId != null && !transactionId.isBlank()) ? transactionId.trim() : null;
            
            // customerName is now plain text for partial search, TIN uses hash
            String customerTinHash = null;
            if (customerTin != null && !customerTin.isEmpty()) {
                customerTinHash = hashService.hash(customerTin);
                log.info("Searching with TIN: plain={}, hash={}", customerTin, customerTinHash);
            }

            List<POSTransaction> results = searchRepository.searchTransactionsNative(
                statusStr, excludePending, transactionTypeStr, businessTransactionTypeStr, cashRegisterIdStr, cashierIdStr, customerName, befaPaymentIdStr,
                bankTransactionIdStr, transactionIdStr, customerTinHash, customerTin, dateFrom, dateTo, offset, sizeNum);

            log.info("Search returned {} results", results.size());

            Long total = searchRepository.countSearchTransactionsNative(
                statusStr, excludePending, transactionTypeStr, businessTransactionTypeStr, cashRegisterIdStr, cashierIdStr, customerName, befaPaymentIdStr,
                bankTransactionIdStr, transactionIdStr, customerTinHash, customerTin, dateFrom, dateTo);
            
            if (results.isEmpty()) {
                Map<String, Object> criteria = new HashMap<>();
                if (statusStr != null) criteria.put("status", statusStr);
                if (transactionTypeStr != null) criteria.put("transactionType", transactionTypeStr);
                if (businessTransactionTypeStr != null) criteria.put("businessTransactionType", businessTransactionTypeStr);
                if (cashRegisterIdStr != null) criteria.put("cashRegisterId", cashRegisterIdStr);
                if (cashierIdStr != null) criteria.put("cashierId", cashierIdStr);
                if (customerName != null) criteria.put("customerName", customerName);
                if (befaPaymentIdStr != null) criteria.put("befaPaymentId", befaPaymentIdStr);
                if (bankTransactionIdStr != null) criteria.put("bankTransactionId", bankTransactionIdStr);
                if (transactionIdStr != null) criteria.put("transactionId", transactionIdStr);
                if (customerTinHash != null) criteria.put("customerTinHash", customerTinHash);
                if (dateFrom != null) criteria.put("dateFrom", dateFrom);
                if (dateTo != null) criteria.put("dateTo", dateTo);
                
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "No transactions found matching the specified criteria",
                                    "criteria", criteria));
            }

            Page<POSTransaction> pageResult = new org.springframework.data.domain.PageImpl<>(results, PageRequest.of(pageNum, sizeNum), total);
            return ResponseEntity.ok(pageResult);
        } catch (Exception e) {
            log.error("Error searching transactions", e);
            String errorMessage = e.getMessage() != null ? e.getMessage() : "Unknown error occurred";
            return ResponseEntity.status(500).body(Map.of("error", errorMessage));
        }
    }

    @GetMapping("/fulltext")
    public ResponseEntity<?> fullTextSearch(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(defaultValue = "false") boolean excludePending,
            @RequestParam(required = false) TransactionType businessTransactionType) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
            Page<POSTransaction> results = searchRepository.fullTextSearch(
                    query, status, excludePending, businessTransactionType, pageable);
            if (results.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "No transactions found matching the search query",
                                    "query", query));
            }
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Error in full-text search", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/refunds/{parentTransactionId}")
    public ResponseEntity<?> findAllRefunds(@PathVariable String parentTransactionId) {
        try {
            List<POSTransaction> refunds = searchRepository.findAllRefundsByParentTransactionId(parentTransactionId);
            if (refunds.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "No refunds found for the specified parent transaction",
                                    "parentTransactionId", parentTransactionId));
            }
            return ResponseEntity.ok(refunds);
        } catch (Exception e) {
            log.error("Error finding refunds", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/reversals/{parentTransactionId}")
    public ResponseEntity<?> findAllReversals(@PathVariable String parentTransactionId) {
        try {
            List<POSTransaction> reversals = searchRepository.findAllReversalsByParentTransactionId(parentTransactionId);
            if (reversals.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "No reversals found for the specified parent transaction",
                                    "parentTransactionId", parentTransactionId));
            }
            return ResponseEntity.ok(reversals);
        } catch (Exception e) {
            log.error("Error finding reversals", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/children/{parentTransactionId}")
    public ResponseEntity<?> findAllChildTransactions(@PathVariable String parentTransactionId) {
        try {
            List<POSTransaction> children = searchRepository.findAllChildTransactions(parentTransactionId);
            if (children.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "No child transactions found for the specified parent transaction",
                                    "parentTransactionId", parentTransactionId));
            }
            return ResponseEntity.ok(children);
        } catch (Exception e) {
            log.error("Error finding child transactions", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/customer-name")
    public ResponseEntity<?> findByCustomerName(
            @RequestParam String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            // customerName is now plain text for partial search
            Pageable pageable = PageRequest.of(page, size);
            Page<POSTransaction> results = searchRepository.findByCustomerName(name, pageable);
            if (results.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "No transactions found for the specified customer name",
                                    "customerName", name));
            }
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Error searching by customer name", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/customer-tin")
    public ResponseEntity<?> findByCustomerTin(
            @RequestParam String tin,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            // Compute hash for the TIN before searching (client sends raw string)
            String tinHash = hashService.hash(tin);
            Pageable pageable = PageRequest.of(page, size);
            // Search by hash or plain text for migration compatibility
            Page<POSTransaction> results = searchRepository.findByCustomerTin(tinHash, tin, pageable);
            if (results.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "No transactions found for the specified customer TIN",
                                    "customerTin", tin));
            }
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Error searching by customer TIN", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/amount-range")
    public ResponseEntity<?> findByAmountRange(
            @RequestParam Long minAmount,
            @RequestParam Long maxAmount,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<POSTransaction> results = searchRepository.findByNetAmountRange(minAmount, maxAmount, pageable);
            if (results.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "No transactions found within the specified amount range",
                                    "minAmount", minAmount, "maxAmount", maxAmount));
            }
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Error searching by amount range", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/card-last-four")
    public ResponseEntity<?> findByCardLastFour(
            @RequestParam String cardLastFour,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<POSTransaction> results = searchRepository.findByCardLastFour(cardLastFour, pageable);
            if (results.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "No transactions found for the specified card last four digits",
                                    "cardLastFour", cardLastFour));
            }
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Error searching by card last four", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/payment-method")
    public ResponseEntity<?> findByPaymentMethod(
            @RequestParam String paymentMethod,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);

            // Convert the String (e.g., "CARD") to the PaymentMethod Enum
            PaymentMethod methodEnum = PaymentMethod.valueOf(paymentMethod.toUpperCase());

            Page<POSTransaction> results = searchRepository.findByPaymentMethod(methodEnum, pageable);
            if (results.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "No transactions found for the specified payment method",
                                    "paymentMethod", paymentMethod));
            }
            return ResponseEntity.ok(results);
        } catch (IllegalArgumentException e) {
            // Handles cases where an invalid payment method string is provided in the query param
            log.error("Invalid payment method supplied: {}", paymentMethod, e);
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid payment method: " + paymentMethod));
        } catch (Exception e) {
            log.error("Error searching by payment method", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/vat-category")
    public ResponseEntity<?> findByVatCategory(
            @RequestParam String vatCategory,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<POSTransaction> results = searchRepository.findByVatCategory(vatCategory, pageable);
            if (results.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "No transactions found for the specified VAT category",
                                    "vatCategory", vatCategory));
            }
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Error searching by VAT category", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/item-code")
    public ResponseEntity<?> findByItemCode(
            @RequestParam String itemCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<POSTransaction> results = searchRepository.findByItemCode(itemCode, pageable);
            if (results.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "No transactions found for the specified item code",
                                    "itemCode", itemCode));
            }
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Error searching by item code", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/recent")
    public ResponseEntity<?> findRecentTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<POSTransaction> results = searchRepository.findRecentTransactions(pageable);
            if (results.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "No recent transactions found"));
            }
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Error finding recent transactions", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/recent-cashier/{cashierId}")
    public ResponseEntity<?> findRecentTransactionsByCashier(
            @PathVariable String cashierId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<POSTransaction> results = searchRepository.findRecentTransactionsByCashier(cashierId, pageable);
            if (results.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "No recent transactions found for the specified cashier",
                                    "cashierId", cashierId));
            }
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Error finding recent transactions by cashier", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/recent-register/{cashRegisterId}")
    public ResponseEntity<?> findRecentTransactionsByRegister(
            @PathVariable String cashRegisterId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<POSTransaction> results = searchRepository.findRecentTransactionsByRegister(cashRegisterId, pageable);
            if (results.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "No recent transactions found for the specified cash register",
                                    "cashRegisterId", cashRegisterId));
            }
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Error finding recent transactions by register", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
