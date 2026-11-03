package com.example.postaxvatsystem.service;

import com.example.postaxvatsystem.dto.BefaPaymentDataDTO;
import com.example.postaxvatsystem.dto.LineItemDTO;
import com.example.postaxvatsystem.dto.PaymentInitiationRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.postaxvatsystem.model.BankPaymentAuditLog;
import com.example.postaxvatsystem.model.POSPaymentTender;
import com.example.postaxvatsystem.model.POSTransaction;
import com.example.postaxvatsystem.model.POSTransactionLineItem;
import com.example.postaxvatsystem.model.PaymentMethod;
import com.example.postaxvatsystem.model.TenderStatus;
import com.example.postaxvatsystem.model.TransactionStatus;
import com.example.postaxvatsystem.model.TransactionType;
import com.example.postaxvatsystem.repository.BankPaymentAuditLogRepository;
import com.example.postaxvatsystem.repository.POSTransactionSearchRepository;
import com.example.postaxvatsystem.service.terminal.TerminalAuthorizationResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Transaction state manager with crash resilience.
 * Uses Propagation.REQUIRES_NEW to isolate database saves from network calls,
 * ensuring PENDING state is committed before hitting the bank API.
 */
@Service
@Slf4j
public class TransactionStateManager {

    private final POSTransactionSearchRepository transactionRepository;
    private final BankPaymentAuditLogRepository auditLogRepository;
    private final VATCalculationService vatCalculationService;
    private final FiscalReceiptService fiscalReceiptService;
    private final ItemManagementService itemManagementService;
    private final BankTransactionIdGenerator bankTransactionIdGenerator;
    private final ObjectMapper objectMapper;
    private final POSConfigurationService configurationService;

    public TransactionStateManager(POSTransactionSearchRepository transactionRepository,
                                  BankPaymentAuditLogRepository auditLogRepository,
                                  VATCalculationService vatCalculationService,
                                  FiscalReceiptService fiscalReceiptService,
                                  ItemManagementService itemManagementService,
                                  BankTransactionIdGenerator bankTransactionIdGenerator,
                                  ObjectMapper objectMapper,
                                  POSConfigurationService configurationService) {
        this.transactionRepository = transactionRepository;
        this.auditLogRepository = auditLogRepository;
        this.vatCalculationService = vatCalculationService;
        this.fiscalReceiptService = fiscalReceiptService;
        this.itemManagementService = itemManagementService;
        this.bankTransactionIdGenerator = bankTransactionIdGenerator;
        this.objectMapper = objectMapper;
        this.configurationService = configurationService;
    }

    /**
     * Creates and immediately saves the PENDING checkpoint to PostgreSQL.
     * Propagation.REQUIRES_NEW isolates this database save from the network call.
     * 
     * This ensures that if the app crashes after this point but before the bank call,
     * the transaction is recorded as PENDING and can be reconciled later.
     * 
     * @param request Payment initiation request
     * @param befa Internal BEFA payment data
     * @return Saved transaction with PENDING status
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public POSTransaction preparePendingTransaction(PaymentInitiationRequest request, BefaPaymentDataDTO befa) {
        log.info("Preparing pending transaction for BEFA payment ID: {}", befa.getBefaPaymentId());

        POSTransaction transaction = new POSTransaction();
        transaction.setTransactionId(generateTransactionId());
        String idempotencyKey = befa.getIdempotencyKey();
        transaction.setIdempotencyKey(idempotencyKey != null && !idempotencyKey.isBlank() && !idempotencyKey.contains("{{")
            ? idempotencyKey : UUID.randomUUID().toString());
        transaction.setBefaPaymentId(befa.getBefaPaymentId());
        transaction.setCashRegisterId(befa.getCashRegisterId() != null && !befa.getCashRegisterId().isEmpty()
            ? befa.getCashRegisterId() : request.getCashRegisterId());
        transaction.setCashierId(befa.getCashierId() != null && !befa.getCashierId().isEmpty()
            ? befa.getCashierId() : request.getCashierId());

        // Persist the BEFA details alongside the transaction so detail views can load them later.
        List<POSTransactionLineItem> lineItems = new ArrayList<>();
        BigDecimal grossAmount = BigDecimal.ZERO;
        BigDecimal vatAmount = BigDecimal.ZERO;
        BigDecimal serviceFeeAmount = BigDecimal.ZERO;
        BigDecimal netAmount = BigDecimal.ZERO;
        String currencyCode = configurationService.getCurrencyCode();
        if (request.getCurrencyCode() != null
                && !request.getCurrencyCode().equalsIgnoreCase(currencyCode)) {
            throw new IllegalArgumentException("Transaction currency must match the configured currency " + currencyCode);
        }
        boolean pricesIncludeVat = Boolean.TRUE.equals(request.getPricesIncludeVat());
        boolean applyCustomerFee = request.getTransactionType() == TransactionType.SALE
                && Boolean.TRUE.equals(request.getApplyCustomerFee());
        if (befa.getLineItems() != null) {
            for (BefaPaymentDataDTO.LineItem befaItem : befa.getLineItems()) {
                LineItemDTO lineItem = new LineItemDTO(
                        befaItem.getItemCode(),
                        befaItem.getItemName(),
                        befaItem.getUnitPrice() != null ? befaItem.getUnitPrice() : BigDecimal.ZERO,
                        Math.toIntExact(befaItem.getQuantity() != null ? befaItem.getQuantity() : 0L),
                        befaItem.getDiscountAmount() != null ? befaItem.getDiscountAmount() : BigDecimal.ZERO,
                        Boolean.TRUE.equals(befaItem.getVatExempt()),
                        befaItem.getVatCategory(),
                        null
                );
                VATCalculationService.LineItemCalculation calculation =
                        vatCalculationService.calculateLineItem(lineItem, pricesIncludeVat, applyCustomerFee);
                POSTransactionLineItem savedItem = new POSTransactionLineItem();
                savedItem.setTransaction(transaction);
                savedItem.setItemCode(lineItem.getItemCode());
                savedItem.setItemName(lineItem.getItemName());
                savedItem.setUnitPrice(lineItem.getUnitPrice());
                savedItem.setQuantity(lineItem.getQuantity());
                savedItem.setLineGrossAmount(calculation.lineGrossAmount());
                savedItem.setLineVatAmount(calculation.lineVatAmount());
                savedItem.setLineServiceFeeAmount(calculation.lineServiceFeeAmount());
                savedItem.setLineNetAmount(calculation.lineNetAmount());
                savedItem.setDiscountAmount(lineItem.getDiscountAmount());
                savedItem.setVatExempt(lineItem.getVatExempt());
                savedItem.setVatCategory(lineItem.getVatCategory());
                savedItem.setVatRate(calculation.vatRate());
                lineItems.add(savedItem);
                grossAmount = grossAmount.add(calculation.lineGrossAmount());
                vatAmount = vatAmount.add(calculation.lineVatAmount());
                serviceFeeAmount = serviceFeeAmount.add(calculation.lineServiceFeeAmount());
                netAmount = netAmount.add(calculation.lineNetAmount());
            }
        }
        if (lineItems.isEmpty()) {
            grossAmount = befa.getAmountInCents() != null ? befa.getAmountInCents() : BigDecimal.ZERO;
            netAmount = grossAmount;
        }
        transaction.setCurrencyCode(currencyCode);
        transaction.setPricesIncludeVat(pricesIncludeVat);
        transaction.setCustomerFeeApplied(applyCustomerFee);
        transaction.setCustomerFeeRate(applyCustomerFee ? vatCalculationService.getServiceFeeRate() : BigDecimal.ZERO);
        transaction.setGrossAmount(grossAmount);
        transaction.setVatAmount(vatAmount);
        transaction.setServiceFeeAmount(serviceFeeAmount);
        transaction.setNetAmount(netAmount);
        setWithholdingVat(transaction, request.getApplyWithholdingVat(),
                request.getWithholdingCertificateNumber(), request.getWithholdingCertificateDate(),
                request.getTransactionType(), vatAmount);
        transaction.setTotalAmountPaid(BigDecimal.ZERO);
        transaction.setCustomerName(befa.getCustomerName() != null ? befa.getCustomerName() : "");
        transaction.setCustomerTin(befa.getCustomerTin() != null ? befa.getCustomerTin() : "");
        transaction.setLineItems(lineItems);

        List<POSPaymentTender> paymentTenders = new ArrayList<>();
        if (request.getTerminalScenarioId() != null && !request.getTerminalScenarioId().isBlank()) {
            BigDecimal amountDue = netAmount.subtract(transaction.getWithholdingVatAmount() == null
                    ? BigDecimal.ZERO : transaction.getWithholdingVatAmount());
            POSPaymentTender tender = new POSPaymentTender();
            tender.setTransaction(transaction);
            tender.setPaymentMethod(PaymentMethod.CARD);
            tender.setPaymentProvider("POS_TERMINAL_SIMULATOR");
            tender.setAmountTendered(amountDue);
            tender.setAmountApplied(amountDue);
            tender.setStatus(TenderStatus.PENDING);
            paymentTenders.add(tender);
        } else if (befa.getPaymentTenders() != null) {
            for (BefaPaymentDataDTO.PaymentTender befaTender : befa.getPaymentTenders()) {
                POSPaymentTender tender = new POSPaymentTender();
                tender.setTransaction(transaction);
                tender.setPaymentMethod(PaymentMethod.valueOf(befaTender.getPaymentMethod().trim().toUpperCase(Locale.ROOT).replace(' ', '_')));
                tender.setPaymentProvider(befaTender.getPaymentProvider());
                BigDecimal amountTendered = befaTender.getAmountTendered() != null
                        ? befaTender.getAmountTendered() : BigDecimal.ZERO;
                tender.setAmountTendered(amountTendered);
                tender.setAmountApplied(amountTendered);
                tender.setStatus(TenderStatus.PENDING);
                paymentTenders.add(tender);
            }
        }
        transaction.setPaymentTenders(paymentTenders);
        adjustTenderAmounts(paymentTenders, netAmount.subtract(
                transaction.getWithholdingVatAmount() == null
                        ? BigDecimal.ZERO : transaction.getWithholdingVatAmount()));

        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setTransactionType(request.getTransactionType() == TransactionType.SALE
                ? TransactionType.SALE : TransactionType.PURCHASE);
        transaction.setBusinessTransactionType(transaction.getTransactionType());

        POSTransaction saved = transactionRepository.save(transaction);
        log.info("Transaction saved with PENDING status. ID: {}, Idempotency Key: {}", 
                saved.getTransactionId(), saved.getIdempotencyKey());

        // Record the initial intent inside Audit Logs
        // Note: In production, requestPayload should be AES-256-GCM encrypted per BRD SEC-1.1
        BankPaymentAuditLog audit = new BankPaymentAuditLog();
        audit.setTransaction(saved);
        audit.setActionType("BANK_API_SUBMISSION_INTENT");
        audit.setStatus("PENDING");
        audit.setRequestPayload("BEFA_PAYMENT_ID:" + befa.getBefaPaymentId());
        auditLogRepository.save(audit);
        log.info("Audit log created for transaction: {}", saved.getTransactionId());

        return saved;
    }

    @Transactional
    public POSTransaction recordTerminalAuthorization(Long internalId, TerminalAuthorizationResult result) {
        POSTransaction transaction = transactionRepository.findById(internalId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found."));
        if (transaction.getPaymentTenders() == null || transaction.getPaymentTenders().isEmpty()) {
            throw new IllegalStateException("The terminal transaction has no payment tender to update.");
        }

        POSPaymentTender tender = transaction.getPaymentTenders().get(0);
        tender.setPaymentMethod(PaymentMethod.CARD);
        tender.setPaymentProvider("POS_TERMINAL_SIMULATOR");
        tender.setCardBrand(result.cardBrand());
        tender.setCardLastFour(result.cardLastFour());
        tender.setAuthCode(result.authorizationCode());
        tender.setRrn(result.retrievalReferenceNumber());
        tender.setTransactionReference(result.bankTransactionId());
        tender.setStatus(switch (result.outcome()) {
            case APPROVED -> TenderStatus.AUTHORIZED;
            case DECLINED -> TenderStatus.FAILED;
            case PENDING -> TenderStatus.PENDING;
        });
        return transactionRepository.save(transaction);
    }

    private void setWithholdingVat(POSTransaction transaction, Boolean requested,
                                   String certificateNumber, java.time.LocalDate certificateDate,
                                   TransactionType businessType, BigDecimal vatAmount) {
        boolean applied = Boolean.TRUE.equals(requested);
        if (!applied) {
            if ((certificateNumber != null && !certificateNumber.isBlank()) || certificateDate != null) {
                throw new IllegalArgumentException("Withholding certificate details require withholding VAT to be selected");
            }
            transaction.setWithholdingVatApplied(false);
            transaction.setWithholdingVatRate(BigDecimal.ZERO);
            transaction.setWithholdingVatAmount(BigDecimal.ZERO);
            return;
        }
        if (certificateNumber == null || certificateNumber.isBlank() || certificateDate == null) {
            throw new IllegalArgumentException("Withholding VAT requires a certificate number and certificate date");
        }
        BigDecimal rate = businessType == TransactionType.SALE
                ? configurationService.getSaleWithholdingVatRate()
                : configurationService.getPurchaseWithholdingVatRate();
        if (rate.signum() <= 0) {
            throw new IllegalArgumentException("Withholding VAT is selected but its configured rate is 0%");
        }
        BigDecimal amount = vatAmount.multiply(rate).setScale(2, java.math.RoundingMode.HALF_UP);
        transaction.setWithholdingVatApplied(true);
        transaction.setWithholdingVatRate(rate);
        transaction.setWithholdingVatAmount(amount);
        transaction.setWithholdingCertificateNumber(certificateNumber.trim());
        transaction.setWithholdingCertificateDate(certificateDate);
    }

    private void adjustTenderAmounts(List<POSPaymentTender> tenders, BigDecimal targetAmount) {
        BigDecimal currentTotal = tenders.stream()
                .map(POSPaymentTender::getAmountTendered)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (currentTotal.signum() <= 0) return;
        int targetCents = targetAmount.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).intValueExact();
        int currentCents = currentTotal.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).intValueExact();
        int assignedCents = 0;
        for (int index = 0; index < tenders.size(); index++) {
            POSPaymentTender tender = tenders.get(index);
            int amountCents = index == tenders.size() - 1
                    ? targetCents - assignedCents
                    : tender.getAmountTendered().movePointRight(2)
                            .multiply(BigDecimal.valueOf(targetCents))
                            .divide(BigDecimal.valueOf(currentCents), 0, java.math.RoundingMode.DOWN)
                            .intValueExact();
            BigDecimal adjusted = BigDecimal.valueOf(amountCents, 2);
            tender.setAmountTendered(adjusted);
            tender.setAmountApplied(adjusted);
            assignedCents += amountCents;
        }
    }

    /**
     * Finalizes a transaction to SUCCESS/SETTLED status after successful bank payment.
     * 
     * @param internalId Internal database ID of the transaction
     * @param responsePayload Raw response from bank (should be encrypted in production)
     */
    @Transactional
    public POSTransaction finalizeToSuccess(Long internalId, String responsePayload) {
        log.info("Finalizing transaction to SETTLED. Internal ID: {}", internalId);

        POSTransaction txn = transactionRepository.findById(internalId)
                .orElseThrow(() -> new RuntimeException("Transaction not found with ID: " + internalId));

        // Generate fiscal receipt (same flow as COMPLETED status)
        var fiscalResult = fiscalReceiptService.generateFiscalReceipt(txn);
        txn.setFiscalSignature(fiscalResult.fiscalSignature());
        txn.setFiscalReference(fiscalResult.fiscalReference());

        txn.setStatus(TransactionStatus.SETTLED);
        txn.setSettledAt(java.time.LocalDateTime.now());
        String bankTransactionId = extractBankTransactionId(responsePayload);
        txn.setBankTransactionId(isUsableBankTransactionId(bankTransactionId)
                ? bankTransactionId : bankTransactionIdGenerator.nextId());
        if (txn.getPaymentTenders() != null) {
            BigDecimal totalAmountPaid = BigDecimal.ZERO;
            for (POSPaymentTender tender : txn.getPaymentTenders()) {
                tender.setStatus(TenderStatus.CAPTURED);
                totalAmountPaid = totalAmountPaid.add(
                        tender.getAmountApplied() != null ? tender.getAmountApplied() : BigDecimal.ZERO);
            }
            txn.setTotalAmountPaid(totalAmountPaid);
        }
        txn.setRefundableAmount(txn.getTotalAmountPaid() == null ? BigDecimal.ZERO : txn.getTotalAmountPaid());
        itemManagementService.applySuccessfulTransaction(txn);
        POSTransaction saved = transactionRepository.save(txn);

        // Record success in audit log
        // Note: responsePayload should be AES-256-GCM encrypted per BRD SEC-1.1
        BankPaymentAuditLog audit = new BankPaymentAuditLog();
        audit.setTransaction(txn);
        audit.setActionType("BANK_API_SUBMISSION_SUCCESS");
        audit.setStatus("SUCCESS");
        audit.setRequestPayload("BEFA_PAYMENT_ID:" + txn.getBefaPaymentId());
        audit.setResponsePayload(responsePayload);
        auditLogRepository.save(audit);

        log.info("Transaction finalized to SETTLED: {}", txn.getTransactionId());
        return saved;
    }

    /**
     * Finalizes a transaction to FAILED status after bank payment failure.
     * 
     * @param internalId Internal database ID of the transaction
     * @param failureMessage Failure reason/message
     */
    @Transactional
    public POSTransaction finalizeToFailure(Long internalId, String failureMessage) {
        log.info("Finalizing transaction to FAILED. Internal ID: {}", internalId);
        
        POSTransaction txn = transactionRepository.findById(internalId)
                .orElseThrow(() -> new RuntimeException("Transaction not found with ID: " + internalId));
        
        txn.setStatus(TransactionStatus.FAILED);
        if (txn.getPaymentTenders() != null) {
            txn.getPaymentTenders().forEach(tender -> tender.setStatus(TenderStatus.FAILED));
        }
        POSTransaction saved = transactionRepository.save(txn);

        // Record failure in audit log
        BankPaymentAuditLog audit = new BankPaymentAuditLog();
        audit.setTransaction(txn);
        audit.setActionType("BANK_API_SUBMISSION_FAILED");
        audit.setStatus("FAILED");
        audit.setRequestPayload("BEFA_PAYMENT_ID:" + txn.getBefaPaymentId());
        audit.setResponsePayload(failureMessage);
        auditLogRepository.save(audit);
        
        log.info("Transaction finalized to FAILED: {}", txn.getTransactionId());
        return saved;
    }

    private String generateTransactionId() {
        return "TXN-" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) +
               "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String extractBankTransactionId(String responsePayload) {
        if (responsePayload == null || responsePayload.isBlank()) {
            return null;
        }
        try {
            JsonNode response = objectMapper.readTree(responsePayload);
            JsonNode body = response.path("body");
            JsonNode source = body.isObject() ? body : response;
            String transactionId = source.path("bankTransactionId").asText();
            return transactionId.isBlank() ? source.path("transactionId").asText() : transactionId;
        } catch (JsonProcessingException exception) {
            log.warn("Bank response was not valid JSON; generating a local bank transaction reference");
            return null;
        }
    }

    private boolean isUsableBankTransactionId(String bankTransactionId) {
        return bankTransactionId != null
                && !bankTransactionId.isBlank()
                && !bankTransactionId.contains("{{");
    }
}
