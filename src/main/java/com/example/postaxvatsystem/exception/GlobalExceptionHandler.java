package com.example.postaxvatsystem.exception;

import com.example.postaxvatsystem.dto.ErrorResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Global exception handler for the application.
 * Catches exceptions and returns user-friendly error responses.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handles MethodArgumentNotValidException (validation errors from @NotBlank, @NotNull, etc.).
     * Returns user-friendly validation error messages.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidationException(MethodArgumentNotValidException ex) {
        log.error("Validation error: {}", ex.getMessage());

        // Collect all field errors
        Map<String, String> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value",
                        (existing, replacement) -> existing
                ));

        Map<String, Object> details = new HashMap<>();
        details.put("fieldErrors", fieldErrors);

        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                "VALIDATION_ERROR",
                "Validation failed for one or more fields",
                details,
                false,
                LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handles DataIntegrityViolationException (NOT NULL constraint violations).
     * Parses the PostgreSQL error message to extract the column name and returns
     * a user-friendly error message.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseDTO> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.error("Data integrity violation: {}", ex.getMessage(), ex);

        String message = ex.getMessage();
        String code = "DB_INTEGRITY_VIOLATION";
        String errorMessage = "Database integrity violation occurred";
        Map<String, Object> details = new HashMap<>();

        // Parse PostgreSQL NOT NULL constraint violation
        Pattern pattern = Pattern.compile("null value in column \"([^\"]+)\"");
        Matcher matcher = pattern.matcher(message);

        if (matcher.find()) {
            String columnName = matcher.group(1);
            String fieldName = convertColumnNameToFieldName(columnName);

            code = "DB_NULL_VALUE_VIOLATION";
            errorMessage = String.format("Field '%s' is required and cannot be null", fieldName);
            details.put("field", fieldName);
            details.put("column", columnName);
        }
        // Parse PostgreSQL UNIQUE constraint violation
        else if (message.contains("violates unique constraint")) {
            Pattern uniquePattern = Pattern.compile("violates unique constraint \"([^\"]+)\"");
            Matcher uniqueMatcher = uniquePattern.matcher(message);

            if (uniqueMatcher.find()) {
                String constraintName = uniqueMatcher.group(1);

                code = "DB_UNIQUE_VIOLATION";
                errorMessage = String.format("A record with this value already exists (constraint: %s)", constraintName);
                details.put("constraint", constraintName);
            }
        }

        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                code,
                errorMessage,
                details,
                false,
                LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handles DuplicateTransactionException when a transaction with the same BEFA payment ID already exists.
     * Returns a 409 CONFLICT status with details about the existing transaction.
     */
    @ExceptionHandler(DuplicateTransactionException.class)
    public ResponseEntity<ErrorResponseDTO> handleDuplicateTransaction(DuplicateTransactionException ex) {
        log.error("Duplicate transaction detected: BEFA ID: {}, Existing TXN: {}, Status: {}",
                ex.getBefaPaymentId(), ex.getExistingTransactionId(), ex.getStatus());

        Map<String, Object> details = new HashMap<>();
        details.put("befaPaymentId", ex.getBefaPaymentId());
        details.put("existingTransactionId", ex.getExistingTransactionId());
        details.put("status", ex.getStatus());

        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                "DUPLICATE_TRANSACTION",
                ex.getMessage(),
                details,
                false,
                LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    /**
     * Handles TransactionNotFoundException when a requested transaction cannot be found.
     * Returns a 404 NOT FOUND status with a clear error message.
     */
    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleTransactionNotFound(TransactionNotFoundException ex) {
        log.error("Transaction not found: {}", ex.getMessage());

        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                "TRANSACTION_NOT_FOUND",
                ex.getMessage(),
                null,
                false,
                LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * Handles ResourceNotFoundException for any entity type.
     * Returns a 404 NOT FOUND status with resource type and ID information.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleResourceNotFound(ResourceNotFoundException ex) {
        log.error("Resource not found: Type={}, ID={}", ex.getResourceType(), ex.getResourceId());

        Map<String, Object> details = new HashMap<>();
        details.put("resourceType", ex.getResourceType());
        details.put("resourceId", ex.getResourceId());

        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                "RESOURCE_NOT_FOUND",
                ex.getMessage(),
                details,
                false,
                LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * Handles IdempotencyKeyExistsException when a duplicate idempotency key is used.
     * Returns a 409 CONFLICT status.
     */
    @ExceptionHandler(IdempotencyKeyExistsException.class)
    public ResponseEntity<ErrorResponseDTO> handleIdempotencyKeyExists(IdempotencyKeyExistsException ex) {
        log.error("Idempotency key conflict: {}", ex.getMessage());

        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                "POS_IDEMPOTENCY_KEY_EXISTS",
                ex.getMessage(),
                null,
                false,
                LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    /**
     * Handles InsufficientPaymentException when payment amount is insufficient.
     * Returns a 400 BAD_REQUEST status.
     */
    @ExceptionHandler(InsufficientPaymentException.class)
    public ResponseEntity<ErrorResponseDTO> handleInsufficientPayment(InsufficientPaymentException ex) {
        log.error("Insufficient payment: {}", ex.getMessage());

        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                "POS_INSUFFICIENT_PAYMENT",
                ex.getMessage(),
                null,
                true,
                LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handles ExactAmountRequiredException when exact payment is required.
     * Returns a 400 BAD_REQUEST status.
     */
    @ExceptionHandler(ExactAmountRequiredException.class)
    public ResponseEntity<ErrorResponseDTO> handleExactAmountRequired(ExactAmountRequiredException ex) {
        log.error("Exact amount required: {}", ex.getMessage());

        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                "POS_EXACT_AMOUNT_REQUIRED",
                ex.getMessage(),
                null,
                false,
                LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handles InvalidTransactionStateException when transaction is in wrong state.
     * Returns a 400 BAD_REQUEST status.
     */
    @ExceptionHandler(InvalidTransactionStateException.class)
    public ResponseEntity<ErrorResponseDTO> handleInvalidTransactionState(InvalidTransactionStateException ex) {
        log.error("Invalid transaction state: {}", ex.getMessage());

        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                "POS_INVALID_STATE",
                ex.getMessage(),
                null,
                false,
                LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handles PreAuthExpiredException when pre-authorization has expired.
     * Returns a 400 BAD_REQUEST status.
     */
    @ExceptionHandler(PreAuthExpiredException.class)
    public ResponseEntity<ErrorResponseDTO> handlePreAuthExpired(PreAuthExpiredException ex) {
        log.error("Pre-authorization expired: {}", ex.getMessage());

        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                "POS_PREAUTH_EXPIRED",
                ex.getMessage(),
                null,
                false,
                LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handles InsufficientFundsException when capture amount exceeds hold.
     * Returns a 400 BAD_REQUEST status.
     */
    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponseDTO> handleInsufficientFunds(InsufficientFundsException ex) {
        log.error("Insufficient funds: {}", ex.getMessage());

        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                "POS_INSUFFICIENT_FUNDS",
                ex.getMessage(),
                null,
                false,
                LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handles RefundWindowExpiredException when refund time window exceeded.
     * Returns a 400 BAD_REQUEST status.
     */
    @ExceptionHandler(RefundWindowExpiredException.class)
    public ResponseEntity<ErrorResponseDTO> handleRefundWindowExpired(RefundWindowExpiredException ex) {
        log.error("Refund window expired: {}", ex.getMessage());

        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                "POS_REFUND_WINDOW_EXPIRED",
                ex.getMessage(),
                null,
                false,
                LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handles RefundAmountExceededException when refund exceeds available balance.
     * Returns a 400 BAD_REQUEST status with details.
     */
    @ExceptionHandler(RefundAmountExceededException.class)
    public ResponseEntity<ErrorResponseDTO> handleRefundAmountExceeded(RefundAmountExceededException ex) {
        log.error("Refund amount exceeded: Requested={}, Available={}, Transaction={}",
                ex.getRequested(), ex.getRemainingRefundable(), ex.getOriginalTransaction());

        Map<String, Object> details = new HashMap<>();
        details.put("requested", ex.getRequested());
        details.put("remainingRefundable", ex.getRemainingRefundable());
        details.put("originalTransaction", ex.getOriginalTransaction());

        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                "POS_REFUND_AMOUNT_EXCEEDED",
                ex.getMessage(),
                details,
                false,
                LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handles SettledTransactionException when attempting to reverse settled transaction.
     * Returns a 400 BAD_REQUEST status.
     */
    @ExceptionHandler(SettledTransactionException.class)
    public ResponseEntity<ErrorResponseDTO> handleSettledTransaction(SettledTransactionException ex) {
        log.error("Settled transaction: {}", ex.getMessage());

        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                "POS_SETTLED_TRANSACTION",
                ex.getMessage(),
                null,
                false,
                LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handles PartialRefundExistsException when partial refund exists.
     * Returns a 400 BAD_REQUEST status.
     */
    @ExceptionHandler(PartialRefundExistsException.class)
    public ResponseEntity<ErrorResponseDTO> handlePartialRefundExists(PartialRefundExistsException ex) {
        log.error("Partial refund exists: {}", ex.getMessage());

        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                "POS_PARTIAL_REFUND_EXISTS",
                ex.getMessage(),
                null,
                false,
                LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handles InsufficientStockException when item stock is insufficient.
     * Returns a 400 BAD_REQUEST status with stock details.
     */
    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ErrorResponseDTO> handleInsufficientStock(InsufficientStockException ex) {
        log.error("Insufficient stock: Item={}, Available={}, Required={}",
                ex.getItemCode(), ex.getAvailable(), ex.getRequired());

        Map<String, Object> details = new HashMap<>();
        details.put("itemCode", ex.getItemCode());
        details.put("available", ex.getAvailable());
        details.put("required", ex.getRequired());

        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                "INSUFFICIENT_STOCK",
                ex.getMessage(),
                details,
                false,
                LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Generic handler for all unhandled runtime exceptions.
     * Returns a 500 INTERNAL_SERVER_ERROR status with raw error details.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponseDTO> handleRuntimeException(RuntimeException ex) {
        log.error("Unhandled runtime exception: {}", ex.getMessage(), ex);

        Map<String, Object> details = new HashMap<>();
        details.put("exceptionType", ex.getClass().getSimpleName());
        details.put("rawMessage", ex.getMessage());

        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred. Please try again later.",
                details,
                false,
                LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Converts database column name to Java field name.
     * Example: "befa_payment_id" -> "befaPaymentId"
     */
    private String convertColumnNameToFieldName(String columnName) {
        if (columnName == null || columnName.isEmpty()) {
            return columnName;
        }
        
        StringBuilder fieldName = new StringBuilder();
        boolean capitalizeNext = false;
        
        for (int i = 0; i < columnName.length(); i++) {
            char c = columnName.charAt(i);
            
            if (c == '_') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                fieldName.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                fieldName.append(Character.toLowerCase(c));
            }
        }
        
        return fieldName.toString();
    }
}
