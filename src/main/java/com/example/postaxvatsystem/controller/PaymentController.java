package com.example.postaxvatsystem.controller;

import com.example.postaxvatsystem.dto.PaymentInitiationRequest;
import com.example.postaxvatsystem.dto.TransactionResponseDTO;
import com.example.postaxvatsystem.dto.WrappedTransactionResponse;
import com.example.postaxvatsystem.model.POSTransaction;
import com.example.postaxvatsystem.repository.POSTransactionSearchRepository;
import com.example.postaxvatsystem.service.BankPaymentService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/pos/bank-payments")
@Slf4j
public class PaymentController {

    private final BankPaymentService bankPaymentService;
    private final POSTransactionSearchRepository transactionRepository;

    public PaymentController(BankPaymentService bankPaymentService, POSTransactionSearchRepository transactionRepository) {
        this.bankPaymentService = bankPaymentService;
        this.transactionRepository = transactionRepository;
    }

    @PostMapping("/preview")
    public ResponseEntity<?> previewPayment(@Valid @RequestBody PaymentInitiationRequest request) {
        return ResponseEntity.ok(bankPaymentService.previewBefaPayment(request));
    }

    @PostMapping("/execute")
    public ResponseEntity<WrappedTransactionResponse> executePayment(@Valid @RequestBody PaymentInitiationRequest request) {
        log.info("Received payment execution request. BEFA ID: {}, Register: {}, Cashier: {}",
                request.getBefaPaymentId(), request.getCashRegisterId(), request.getCashierId());

        try {
            POSTransaction result = bankPaymentService.processBankPayment(request);
            log.info("Payment processed. Transaction ID: {}, Status: {}",
                    result.getTransactionId(), result.getStatus());
            TransactionResponseDTO body = mapToTransactionResponseDTO(result);
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            WrappedTransactionResponse wrappedResponse = new WrappedTransactionResponse(200, headers, body);

            return ResponseEntity.ok(wrappedResponse);
        } catch (Exception e) {
            log.error("Error executing payment: {}", e.getMessage(), e);
            throw e;
        }
    }

    @GetMapping("/status/{befaPaymentId}")
    public ResponseEntity<WrappedTransactionResponse> getTransactionByBefaPaymentId(@PathVariable String befaPaymentId) {
        log.info("Looking up transaction by BEFA payment ID: {}", befaPaymentId);

        var transactions = transactionRepository.findByBefaPaymentId(befaPaymentId);
        if (transactions.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        POSTransaction transaction = transactions.get(0);
        TransactionResponseDTO body = mapToTransactionResponseDTO(transaction);
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        WrappedTransactionResponse wrappedResponse = new WrappedTransactionResponse(200, headers, body);
        return ResponseEntity.ok(wrappedResponse);
    }

    @GetMapping("/lookup/{identifier}")
    public ResponseEntity<WrappedTransactionResponse> lookupTransaction(@PathVariable String identifier) {
        log.info("Looking up transaction by identifier: {}", identifier);

        return transactionRepository.findByAnyIdentifier(identifier)
                .map(transaction -> {
                    TransactionResponseDTO body = mapToTransactionResponseDTO(transaction);
                    Map<String, String> headers = new HashMap<>();
                    headers.put("Content-Type", "application/json");
                    WrappedTransactionResponse wrappedResponse = new WrappedTransactionResponse(200, headers, body);
                    return ResponseEntity.ok(wrappedResponse);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/lookup/{identifier}/all")
    public ResponseEntity<?> lookupAllTransactions(@PathVariable String identifier) {
        log.info("Looking up all transactions by identifier: {}", identifier);

        List<POSTransaction> transactions = transactionRepository.findAllByAnyIdentifier(identifier);
        List<TransactionResponseDTO> dtos = transactions.stream()
                .map(this::mapToTransactionResponseDTO)
                .toList();

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");

        return ResponseEntity.ok(Map.of(
                "statusCode", 200,
                "headers", headers,
                "body", dtos
        ));
    }

    private TransactionResponseDTO mapToTransactionResponseDTO(POSTransaction transaction) {
        TransactionResponseDTO dto = new TransactionResponseDTO();
        dto.setTransactionId(transaction.getTransactionId());
        dto.setBefaPaymentId(transaction.getBefaPaymentId());
        dto.setIdempotencyKey(transaction.getIdempotencyKey());
        dto.setBankTransactionId(transaction.getBankTransactionId());
        dto.setCashRegisterId(transaction.getCashRegisterId());
        dto.setCashierId(transaction.getCashierId());
        dto.setCustomerName(transaction.getCustomerName());
        dto.setCustomerTin(transaction.getCustomerTin());
        dto.setStatus(transaction.getStatus());
        dto.setTransactionType(transaction.getTransactionType());
        dto.setBusinessTransactionType(transaction.getBusinessTransactionType());
        dto.setCurrencyCode(transaction.getCurrencyCode());
        dto.setPricesIncludeVat(transaction.getPricesIncludeVat());
        dto.setCustomerFeeApplied(transaction.getCustomerFeeApplied());
        dto.setCustomerFeeRate(transaction.getCustomerFeeRate());
        dto.setGrossAmount(transaction.getGrossAmount());
        dto.setVatAmount(transaction.getVatAmount());
        dto.setServiceFeeAmount(transaction.getServiceFeeAmount());
        dto.setNetAmount(transaction.getNetAmount());
        dto.setWithholdingVatApplied(transaction.getWithholdingVatApplied());
        dto.setWithholdingVatRate(transaction.getWithholdingVatRate());
        dto.setWithholdingVatAmount(transaction.getWithholdingVatAmount());
        dto.setWithholdingCertificateNumber(transaction.getWithholdingCertificateNumber());
        dto.setWithholdingCertificateDate(transaction.getWithholdingCertificateDate());
        dto.setTotalAmountPaid(transaction.getTotalAmountPaid());
        dto.setRefundedAmount(transaction.getRefundedAmount());
        dto.setRefundableAmount(transaction.getRefundableAmount());
        dto.setFiscalSignature(transaction.getFiscalSignature());
        dto.setFiscalReference(transaction.getFiscalReference());
        dto.setFiscalSubmissionStatus(transaction.getFiscalSubmissionStatus());
        dto.setFiscalSubmissionMode(transaction.getFiscalSubmissionMode());
        dto.setFiscalAuthorityResponseCode(transaction.getFiscalAuthorityResponseCode());
        dto.setFiscalAuthorityResponseMessage(transaction.getFiscalAuthorityResponseMessage());
        dto.setFiscalSubmittedToAuthorityAt(transaction.getFiscalSubmittedToAuthorityAt());
        dto.setCreatedAt(transaction.getCreatedAt());
        dto.setAuthorizedAt(transaction.getAuthorizedAt());
        dto.setSettledAt(transaction.getSettledAt());
        dto.setCapturedAt(transaction.getCapturedAt());
        dto.setPreauthExpiresAt(transaction.getPreauthExpiresAt());
        dto.setParentTransactionId(transaction.getParentTransactionId());
        dto.setCancellationReason(transaction.getCancellationReason());
        dto.setPreauthHoldAmount(transaction.getPreauthHoldAmount());
        if (transaction.getPaymentTenders() != null) {
            dto.setPaymentTenders(transaction.getPaymentTenders().stream()
                    .map(tender -> new com.example.postaxvatsystem.dto.PaymentTenderResponseDTO(
                            tender.getId(),
                            tender.getPaymentMethod(),
                            tender.getPaymentProvider(),
                            tender.getAmountTendered(),
                            tender.getAmountApplied(),
                            tender.getCashChange(),
                            tender.getCardLastFour(),
                            tender.getCardBrand(),
                            tender.getAuthCode(),
                            tender.getRrn(),
                            tender.getStatus()))
                    .toList());
        }
        return dto;
    }
}
