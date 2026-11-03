package com.example.postaxvatsystem.controller;

import com.example.postaxvatsystem.dto.*;
import com.example.postaxvatsystem.exception.*;
import com.example.postaxvatsystem.service.POSTransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/pos/transactions")
@RequiredArgsConstructor
@Slf4j
public class POSTransactionController {

    private final POSTransactionService transactionService;

    @PostMapping("/purchase")
    public ResponseEntity<?> processPurchase(@Valid @RequestBody PurchaseRequestDTO request) {
        // In production, actorId and actorRole would come from JWT token
        String actorId = request.getCashierId();
        String actorRole = "CASHIER";
        
        TransactionResponseDTO response = transactionService.processPurchase(request, actorId, actorRole);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/preauth")
    public ResponseEntity<?> processPreAuth(@Valid @RequestBody PreAuthRequestDTO request) {
        String actorId = request.getCashierId();
        String actorRole = "CASHIER";
        
        TransactionResponseDTO response = transactionService.processPreAuth(request, actorId, actorRole);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/capture")
    public ResponseEntity<?> capturePreAuth(@PathVariable String id, 
                                           @Valid @RequestBody CaptureRequestDTO request) {
        String actorId = "SYSTEM";
        String actorRole = "SENIOR_CASHIER";
        
        TransactionResponseDTO response = transactionService.capturePreAuth(id, request, actorId, actorRole);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/refund")
    public ResponseEntity<?> processRefund(@PathVariable String id, 
                                          @Valid @RequestBody RefundRequestDTO request) {
        String actorId = "SYSTEM";
        String actorRole = "MANAGER";
        
        TransactionResponseDTO response = transactionService.processRefund(id, request, actorId, actorRole);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/reverse")
    public ResponseEntity<?> processReversal(@PathVariable String id, 
                                            @Valid @RequestBody ReversalRequestDTO request) {
        String actorId = "SYSTEM";
        String actorRole = "MANAGER";
        
        TransactionResponseDTO response = transactionService.processReversal(id, request, actorId, actorRole);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getTransaction(@PathVariable String id) {
        TransactionResponseDTO response = transactionService.getTransaction(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancelTransaction(@PathVariable String id,
                                               @Valid @RequestBody ReversalRequestDTO request) {
        TransactionResponseDTO response = transactionService.cancelPreAuth(id, request, "SYSTEM", "MANAGER");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/pending")
    public ResponseEntity<?> getPendingTransactions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) {
            return ResponseEntity.badRequest()
                    .body(createErrorResponse("POS_INVALID_PAGINATION",
                            "Page must be non-negative and size must be between 1 and 100", false));
        }
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            return ResponseEntity.badRequest()
                    .body(createErrorResponse("POS_INVALID_DATE_RANGE",
                            "dateFrom must be on or before dateTo", false));
        }
        try {
            org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
            org.springframework.data.domain.Page<PendingTransactionDTO> pendingTransactions =
                    transactionService.getPendingTransactions(dateFrom, dateTo, pageable);
            return ResponseEntity.ok(pendingTransactions);
        } catch (Exception e) {
            log.error("Error getting pending transactions", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("POS_INTERNAL_ERROR", e.getMessage(), true));
        }
    }

    private ErrorResponseDTO createErrorResponse(String code, String message, Boolean retryable) {
        return createErrorResponse(code, message, retryable, null);
    }

    private ErrorResponseDTO createErrorResponse(String code, String message, Boolean retryable, Map<String, Object> details) {
        ErrorResponseDTO error = new ErrorResponseDTO();
        error.setCode(code);
        error.setMessage(message);
        error.setDetails(details);
        error.setRetryable(retryable);
        error.setTimestamp(LocalDateTime.now());
        return error;
    }
}
