package com.example.postaxvatsystem.service;

import com.example.postaxvatsystem.dto.*;
import com.example.postaxvatsystem.exception.*;
import com.example.postaxvatsystem.model.*;
import com.example.postaxvatsystem.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.collection.spi.PersistentCollection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class POSTransactionService {

    private final POSTransactionRepository transactionRepository;
    private final POSTransactionLineItemRepository lineItemRepository;
    private final POSPaymentTenderRepository paymentTenderRepository;
    private final POSAuditLogRepository auditLogRepository;
    private final VATCalculationService vatCalculationService;
    private final FiscalReceiptService fiscalReceiptService;
    private final ItemManagementService itemManagementService;
    private final BankTransactionIdGenerator bankTransactionIdGenerator;
    private final POSTransactionCaptureRepository captureRepository;
    private final PreAuthorizationOperations preAuthorizationOperations;
    private final POSConfigurationService configurationService;

    @Value("${vat.refund.window.days}")
    private Integer refundWindowDays;

    @Value("${preauth.default.expiry.hours:72}")
    private Integer defaultPreAuthExpiryHours;

    private String generateBankTransactionId() {
        return bankTransactionIdGenerator.nextId();
    }

    private BigDecimal calculateWithholdingVat(Boolean requested, String certificateNumber,
                                                java.time.LocalDate certificateDate,
                                                TransactionType businessType, BigDecimal vatAmount) {
        if (!Boolean.TRUE.equals(requested)) {
            if ((certificateNumber != null && !certificateNumber.isBlank()) || certificateDate != null) {
                throw new IllegalArgumentException("Withholding certificate details require withholding VAT to be selected");
            }
            return BigDecimal.ZERO;
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
        return vatAmount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    private void applyWithholdingVat(POSTransaction transaction, Boolean requested,
                                    String certificateNumber, java.time.LocalDate certificateDate,
                                    TransactionType businessType, BigDecimal vatAmount) {
        BigDecimal amount = calculateWithholdingVat(requested, certificateNumber,
                certificateDate, businessType, vatAmount);
        boolean applied = Boolean.TRUE.equals(requested);
        transaction.setWithholdingVatApplied(applied);
        transaction.setWithholdingVatRate(applied
                ? (businessType == TransactionType.SALE
                    ? configurationService.getSaleWithholdingVatRate()
                    : configurationService.getPurchaseWithholdingVatRate())
                : BigDecimal.ZERO);
        transaction.setWithholdingVatAmount(amount);
        transaction.setWithholdingCertificateNumber(applied ? certificateNumber.trim() : null);
        transaction.setWithholdingCertificateDate(applied ? certificateDate : null);
    }

    @Transactional
    public TransactionResponseDTO processPurchase(PurchaseRequestDTO request, String actorId, String actorRole) {
        log.info("Processing purchase transaction for cashier: {}", request.getCashierId());

        String idempotencyKey = request.getIdempotencyKey();
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.contains("{{")) {
            idempotencyKey = UUID.randomUUID().toString();
        } else {
            idempotencyKey = idempotencyKey.trim().toUpperCase();
        }
        request.setIdempotencyKey(idempotencyKey);

        // Check idempotency
        transactionRepository.findByIdempotencyKey(idempotencyKey)
                .ifPresent(existing -> {
                    throw new IdempotencyKeyExistsException("Transaction with this idempotency key already exists");
                });

        // Check for duplicate BEFA payment ID
        if (request.getBefaPaymentId() != null && !request.getBefaPaymentId().isEmpty()) {
            String normalizedBefaPaymentId = request.getBefaPaymentId().trim().toUpperCase();
            var existingTransactions = transactionRepository.findByBefaPaymentId(normalizedBefaPaymentId);
            if (!existingTransactions.isEmpty()) {
                POSTransaction existing = existingTransactions.get(0);
                throw new DuplicateTransactionException(
                        "Transaction with BEFA payment ID " + normalizedBefaPaymentId + " has already been processed",
                        normalizedBefaPaymentId,
                        existing.getTransactionId(),
                        existing.getStatus().name()
                );
            }
        }

        // Validate cash register and cashier assignment
        validateCashRegisterAndCashier(request.getCashRegisterId(), request.getCashierId());

        // Calculate VAT and totals
        String currencyCode = requireCurrentCurrency(request.getCurrencyCode());
        boolean pricesIncludeVat = Boolean.TRUE.equals(request.getPricesIncludeVat());
        VATCalculationService.TransactionCalculation calculation = vatCalculationService.calculateTransaction(
                request.getLineItems(), pricesIncludeVat, false);

        // Validate payment tenders
        BigDecimal withholdingVatAmount = calculateWithholdingVat(
                request.getApplyWithholdingVat(), request.getWithholdingCertificateNumber(),
                request.getWithholdingCertificateDate(), TransactionType.PURCHASE, calculation.totalVatAmount());
        BigDecimal amountDue = calculation.totalNetAmount().subtract(withholdingVatAmount);
        validatePaymentTenders(request.getPaymentTenders(), amountDue, false);

        // Create transaction
        POSTransaction transaction = new POSTransaction();
        transaction.setTransactionId(generateTransactionId());
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setTransactionType(TransactionType.PURCHASE);
        transaction.setBusinessTransactionType(TransactionType.PURCHASE);
        transaction.setCurrencyCode(currencyCode);
        transaction.setPricesIncludeVat(pricesIncludeVat);
        transaction.setCustomerFeeApplied(false);
        transaction.setCustomerFeeRate(BigDecimal.ZERO);
        transaction.setCashRegisterId(request.getCashRegisterId());
        transaction.setCashierId(request.getCashierId());
        transaction.setIdempotencyKey(request.getIdempotencyKey());
        transaction.setGrossAmount(calculation.totalGrossAmount());
        transaction.setVatAmount(calculation.totalVatAmount());
        transaction.setServiceFeeAmount(calculation.totalServiceFeeAmount());
        transaction.setNetAmount(calculation.totalNetAmount());
        applyWithholdingVat(transaction, request.getApplyWithholdingVat(),
                request.getWithholdingCertificateNumber(), request.getWithholdingCertificateDate(),
                TransactionType.PURCHASE, calculation.totalVatAmount());
        transaction.setCustomerName(request.getCustomerName());
        transaction.setCustomerTin(request.getCustomerTin());
        transaction.setBefaPaymentId(request.getBefaPaymentId() != null ? request.getBefaPaymentId().trim().toUpperCase() : null);
        transaction.setBankTransactionId(isUsableBankTransactionId(request.getBankTransactionId())
            ? request.getBankTransactionId().trim().toUpperCase() : null);
        transaction.setRefundableAmount(amountDue);
        transaction.setRefundedAmount(BigDecimal.ZERO);
        transaction.setTotalAmountPaid(BigDecimal.ZERO);

        transaction = transactionRepository.save(transaction);

        // Create line items
        List<LineItemDTO> lineItemDTOs = request.getLineItems();
        List<VATCalculationService.LineItemCalculation> lineCalculations =
                calculation.orderedLineCalculations();
        for (int i = 0; i < lineItemDTOs.size(); i++) {
            LineItemDTO lineItemDTO = lineItemDTOs.get(i);
            String normalizedItemCode = lineItemDTO.getItemCode() != null
                    ? lineItemDTO.getItemCode().trim().toUpperCase(Locale.ROOT) : null;
            VATCalculationService.LineItemCalculation calc = lineCalculations.get(i);

            POSTransactionLineItem lineItem = new POSTransactionLineItem();
            lineItem.setTransaction(transaction);
            lineItem.setItemCode(normalizedItemCode);
            lineItem.setItemName(lineItemDTO.getItemName());
            lineItem.setUnitPrice(lineItemDTO.getUnitPrice());
            lineItem.setQuantity(lineItemDTO.getQuantity());
            lineItem.setLineGrossAmount(calc.lineGrossAmount());
            lineItem.setLineVatAmount(calc.lineVatAmount());
            lineItem.setLineServiceFeeAmount(calc.lineServiceFeeAmount());
            lineItem.setLineNetAmount(calc.lineNetAmount());
            lineItem.setDiscountAmount(lineItemDTO.getDiscountAmount() != null
                    ? lineItemDTO.getDiscountAmount() : BigDecimal.ZERO);
            lineItem.setVatExempt(lineItemDTO.getVatExempt());
            lineItem.setVatCategory(lineItemDTO.getVatCategory());
            lineItem.setVatRate(calc.vatRate());
            lineItem.setItemDescription(lineItemDTO.getItemDescription());

            lineItemRepository.save(lineItem);
        }

        // Process payment authorization
        BigDecimal totalAmountPaid = BigDecimal.ZERO;
        for (PaymentTenderDTO tenderDTO : request.getPaymentTenders()) {
            PaymentGatewayResult authResult = processPaymentAuthorization(tenderDTO);
            
            POSPaymentTender tender = new POSPaymentTender();
            tender.setTransaction(transaction);
            tender.setPaymentMethod(tenderDTO.getPaymentMethod());
            tender.setPaymentProvider(tenderDTO.getPaymentProvider());
            tender.setAmountTendered(tenderDTO.getAmountTendered());
            tender.setAmountApplied(tenderDTO.getAmountTendered());
            tender.setCardLastFour(authResult.cardLastFour());
            tender.setCardBrand(authResult.cardBrand());
            tender.setAuthCode(authResult.authCode());
            tender.setRrn(authResult.rrn());
            tender.setStatus(TenderStatus.AUTHORIZED);
            
            paymentTenderRepository.save(tender);
            totalAmountPaid = totalAmountPaid.add(tenderDTO.getAmountTendered());
        }

        transaction.setTotalAmountPaid(totalAmountPaid);
        transaction.setStatus(TransactionStatus.AUTHORIZED);
        transaction.setAuthorizedAt(LocalDateTime.now());
        transaction = transactionRepository.save(transaction);

        // Capture payment
        transaction.setStatus(TransactionStatus.CAPTURED);
        transaction.setCapturedAt(LocalDateTime.now());
        transaction = transactionRepository.save(transaction);

        // Generate fiscal receipt
        FiscalReceiptResult fiscalResult = fiscalReceiptService.generateFiscalReceipt(transaction);
        transaction.setFiscalSignature(fiscalResult.fiscalSignature());
        transaction.setFiscalReference(fiscalResult.fiscalReference());

        // Complete transaction
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setSettledAt(LocalDateTime.now());
        if (transaction.getBankTransactionId() == null) {
            transaction.setBankTransactionId(generateBankTransactionId());
        }
        transaction = transactionRepository.save(transaction);
        itemManagementService.applySuccessfulTransaction(transaction);

        // Log audit
        createAuditLog(transaction, actorId, actorRole, "PURCHASE", 
                TransactionStatus.PENDING, TransactionStatus.COMPLETED, null);

        log.info("Purchase transaction completed: {}", transaction.getTransactionId());
        return mapToResponseDTO(transaction);
    }

    private boolean isUsableBankTransactionId(String bankTransactionId) {
        return bankTransactionId != null
                && !bankTransactionId.isBlank()
                && !bankTransactionId.contains("{{");
    }

    private void applyCapturedAmounts(POSTransaction transaction, BigDecimal captureAmount) {
        if (Boolean.TRUE.equals(transaction.getWithholdingVatApplied())) return;
        List<POSTransactionLineItem> lineItems = transaction.getLineItems();
        if (lineItems == null || lineItems.isEmpty()) {
            List<POSTransactionLineItem> storedLineItems =
                    lineItemRepository.findByTransactionId(transaction.getId());
            if (lineItems == null) {
                lineItems = new ArrayList<>(storedLineItems);
                transaction.setLineItems(lineItems);
            } else if (lineItems instanceof PersistentCollection) {
                lineItems.addAll(storedLineItems);
            } else {
                lineItems = new ArrayList<>(storedLineItems);
                transaction.setLineItems(lineItems);
            }
        }
        if (lineItems.isEmpty()) {
            transaction.setGrossAmount(captureAmount);
            transaction.setVatAmount(BigDecimal.ZERO);
            transaction.setServiceFeeAmount(BigDecimal.ZERO);
            transaction.setNetAmount(captureAmount);
            return;
        }

        BigDecimal originalNetAmount = valueOrZero(transaction.getPreauthHoldAmount());
        if (originalNetAmount.signum() <= 0) {
            throw new InvalidTransactionStateException("Pre-auth transaction has no positive amount to capture");
        }
        BigDecimal ratio = captureAmount.divide(originalNetAmount, 12, RoundingMode.HALF_UP);
        BigDecimal grossAmount = BigDecimal.ZERO;
        BigDecimal vatAmount = BigDecimal.ZERO;
        BigDecimal serviceFeeAmount = BigDecimal.ZERO;
        BigDecimal netAmount = BigDecimal.ZERO;

        for (POSTransactionLineItem lineItem : lineItems) {
            BigDecimal lineGross = scale(lineItem.getLineGrossAmount(), ratio);
            BigDecimal lineVat = scale(lineItem.getLineVatAmount(), ratio);
            BigDecimal lineServiceFee = scale(lineItem.getLineServiceFeeAmount(), ratio);
            BigDecimal lineNet = scale(lineItem.getLineNetAmount(), ratio);
            lineItem.setLineGrossAmount(lineGross);
            lineItem.setLineVatAmount(lineVat);
            lineItem.setLineServiceFeeAmount(lineServiceFee);
            lineItem.setLineNetAmount(lineNet);
            grossAmount = grossAmount.add(lineGross);
            vatAmount = vatAmount.add(lineVat);
            serviceFeeAmount = serviceFeeAmount.add(lineServiceFee);
            netAmount = netAmount.add(lineNet);
        }

        BigDecimal roundingDifference = captureAmount.subtract(netAmount);
        if (roundingDifference.signum() != 0) {
            POSTransactionLineItem lastLine = lineItems.get(lineItems.size() - 1);
            lastLine.setLineNetAmount(lastLine.getLineNetAmount().add(roundingDifference));
            lastLine.setLineGrossAmount(lastLine.getLineGrossAmount().add(roundingDifference));
            grossAmount = grossAmount.add(roundingDifference);
        }
        transaction.setGrossAmount(grossAmount);
        transaction.setVatAmount(vatAmount);
        transaction.setServiceFeeAmount(serviceFeeAmount);
        transaction.setNetAmount(captureAmount);
    }

    private BigDecimal scale(BigDecimal amount, BigDecimal ratio) {
        return (amount == null ? BigDecimal.ZERO : amount)
                .multiply(ratio)
                .setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional
    public TransactionResponseDTO processPreAuth(PreAuthRequestDTO request, String actorId, String actorRole) {
        log.info("Processing pre-auth transaction for cashier: {}", request.getCashierId());
        TransactionType businessType = request.getBusinessTransactionType() == null
                ? TransactionType.PURCHASE : request.getBusinessTransactionType();
        if (businessType != TransactionType.SALE && businessType != TransactionType.PURCHASE) {
            throw new IllegalArgumentException("Pre-authorization must be associated with a sale or purchase");
        }

        String idempotencyKey = request.getIdempotencyKey();
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.contains("{{")) {
            idempotencyKey = UUID.randomUUID().toString();
        } else {
            idempotencyKey = idempotencyKey.trim().toUpperCase();
        }
        request.setIdempotencyKey(idempotencyKey);

        // Check idempotency
        transactionRepository.findByIdempotencyKey(idempotencyKey)
                .ifPresent(existing -> {
                    throw new IdempotencyKeyExistsException("Transaction with this idempotency key already exists");
                });

        // Check for duplicate BEFA payment ID
        if (request.getBefaPaymentId() != null && !request.getBefaPaymentId().isEmpty()) {
            String normalizedBefaPaymentId = request.getBefaPaymentId().trim().toUpperCase();
            var existingTransactions = transactionRepository.findByBefaPaymentId(normalizedBefaPaymentId);
            if (!existingTransactions.isEmpty()) {
                POSTransaction existing = existingTransactions.get(0);
                throw new DuplicateTransactionException(
                        "Transaction with BEFA payment ID " + normalizedBefaPaymentId + " has already been processed",
                        normalizedBefaPaymentId,
                        existing.getTransactionId(),
                        existing.getStatus().name()
                );
            }
        }

        // Validate cash register and cashier assignment
        validateCashRegisterAndCashier(request.getCashRegisterId(), request.getCashierId());

        // Create transaction
        POSTransaction transaction = new POSTransaction();
        transaction.setTransactionId(generateTransactionId());
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setTransactionType(TransactionType.PRE_AUTH);
        transaction.setBusinessTransactionType(businessType);
        String currencyCode = requireCurrentCurrency(request.getCurrencyCode());
        boolean pricesIncludeVat = Boolean.TRUE.equals(request.getPricesIncludeVat());
        boolean applyCustomerFee = businessType == TransactionType.SALE
                && Boolean.TRUE.equals(request.getApplyCustomerFee());
        transaction.setCurrencyCode(currencyCode);
        transaction.setPricesIncludeVat(pricesIncludeVat);
        transaction.setCustomerFeeApplied(applyCustomerFee);
        transaction.setCustomerFeeRate(applyCustomerFee
                ? vatCalculationService.getServiceFeeRate() : BigDecimal.ZERO);
        transaction.setCashRegisterId(request.getCashRegisterId());
        transaction.setCashierId(request.getCashierId());
        transaction.setIdempotencyKey(request.getIdempotencyKey());
        VATCalculationService.TransactionCalculation calculation = request.getLineItems() == null
                || request.getLineItems().isEmpty()
                ? null
                : vatCalculationService.calculateTransaction(request.getLineItems(), pricesIncludeVat, applyCustomerFee);
        if (calculation == null && Boolean.TRUE.equals(request.getApplyWithholdingVat())) {
            throw new IllegalArgumentException("Withholding VAT requires itemized VAT details");
        }
        BigDecimal withholdingVatAmount = calculation == null
                ? calculateWithholdingVat(request.getApplyWithholdingVat(),
                        request.getWithholdingCertificateNumber(), request.getWithholdingCertificateDate(),
                        businessType, BigDecimal.ZERO)
                : calculateWithholdingVat(request.getApplyWithholdingVat(),
                        request.getWithholdingCertificateNumber(), request.getWithholdingCertificateDate(),
                        businessType, calculation.totalVatAmount());
        BigDecimal invoiceAmount = calculation == null ? request.getPreauthHoldAmount() : calculation.totalNetAmount();
        BigDecimal holdAmount = invoiceAmount.subtract(withholdingVatAmount);
        if (holdAmount.signum() <= 0) {
            throw new IllegalArgumentException("Pre-authorization amount must be positive");
        }
        validatePaymentTenders(request.getPaymentTenders(), holdAmount, true);
        transaction.setGrossAmount(calculation == null ? request.getPreauthHoldAmount() : calculation.totalGrossAmount());
        transaction.setVatAmount(calculation == null ? BigDecimal.ZERO : calculation.totalVatAmount());
        transaction.setServiceFeeAmount(calculation == null ? BigDecimal.ZERO : calculation.totalServiceFeeAmount());
        transaction.setNetAmount(calculation == null ? request.getPreauthHoldAmount() : calculation.totalNetAmount());
        applyWithholdingVat(transaction, request.getApplyWithholdingVat(),
                request.getWithholdingCertificateNumber(), request.getWithholdingCertificateDate(),
                businessType, calculation == null ? BigDecimal.ZERO : calculation.totalVatAmount());
        transaction.setCustomerName(request.getCustomerName());
        transaction.setBefaPaymentId(request.getBefaPaymentId() != null ? request.getBefaPaymentId().trim().toUpperCase() : null);
        transaction.setBankTransactionId(request.getBankTransactionId() != null && !request.getBankTransactionId().isEmpty()
            ? request.getBankTransactionId().trim().toUpperCase() : null);
        transaction.setPreauthHoldAmount(holdAmount);
        int expiryHours = request.getExpiresInHours() == null
                ? defaultPreAuthExpiryHours : request.getExpiresInHours();
        BigDecimal autoCaptureAmount = request.getAutoCaptureAmount() == null
                ? BigDecimal.ZERO : request.getAutoCaptureAmount();
        if (autoCaptureAmount.compareTo(holdAmount) > 0) {
            throw new IllegalArgumentException("Automatic capture amount cannot exceed the pre-authorization hold");
        }
        if (Boolean.TRUE.equals(transaction.getWithholdingVatApplied())
                && autoCaptureAmount.signum() > 0 && autoCaptureAmount.compareTo(holdAmount) != 0) {
            throw new IllegalArgumentException("Withholding VAT pre-authorizations must be captured for the full cash/card balance");
        }
        transaction.setPreauthExpiresAt(LocalDateTime.now().plusHours(expiryHours));
        transaction.setAutoCaptureAmount(autoCaptureAmount);
        transaction.setManualCaptureOccurred(false);
        transaction.setRefundableAmount(BigDecimal.ZERO);
        transaction.setRefundedAmount(BigDecimal.ZERO);
        transaction.setTotalAmountPaid(BigDecimal.ZERO);

        transaction = transactionRepository.save(transaction);

        List<POSTransactionLineItem> preAuthLineItems = new ArrayList<>();
        if (request.getLineItems() != null) {
            for (LineItemDTO lineItemDTO : request.getLineItems()) {
                String normalizedItemCode = lineItemDTO.getItemCode() != null
                        ? lineItemDTO.getItemCode().trim().toUpperCase(Locale.ROOT) : null;
                VATCalculationService.LineItemCalculation lineCalculation =
                        vatCalculationService.calculateLineItem(lineItemDTO, pricesIncludeVat, applyCustomerFee);
                POSTransactionLineItem lineItem = new POSTransactionLineItem();
                lineItem.setTransaction(transaction);
                lineItem.setItemCode(normalizedItemCode);
                lineItem.setItemName(lineItemDTO.getItemName());
                lineItem.setUnitPrice(lineItemDTO.getUnitPrice());
                lineItem.setQuantity(lineItemDTO.getQuantity());
                lineItem.setLineGrossAmount(lineCalculation.lineGrossAmount());
                lineItem.setLineVatAmount(lineCalculation.lineVatAmount());
                lineItem.setLineServiceFeeAmount(lineCalculation.lineServiceFeeAmount());
                lineItem.setLineNetAmount(lineCalculation.lineNetAmount());
                lineItem.setDiscountAmount(lineItemDTO.getDiscountAmount() != null
                        ? lineItemDTO.getDiscountAmount() : BigDecimal.ZERO);
                lineItem.setVatExempt(lineItemDTO.getVatExempt());
                lineItem.setVatCategory(lineItemDTO.getVatCategory());
                lineItem.setVatRate(lineCalculation.vatRate());
                lineItem.setItemDescription(lineItemDTO.getItemDescription());
                preAuthLineItems.add(lineItemRepository.save(lineItem));
            }
        }
        transaction.setLineItems(preAuthLineItems);

        // Process payment authorization
        List<POSPaymentTender> preAuthTenders = new ArrayList<>();
        for (PaymentTenderDTO tenderDTO : request.getPaymentTenders()) {
            PaymentGatewayResult authResult = processPaymentAuthorization(tenderDTO);
            
            POSPaymentTender tender = new POSPaymentTender();
            tender.setTransaction(transaction);
            tender.setPaymentMethod(tenderDTO.getPaymentMethod());
            tender.setPaymentProvider(tenderDTO.getPaymentProvider());
            tender.setAmountTendered(tenderDTO.getAmountTendered());
            tender.setAmountApplied(BigDecimal.ZERO);
            tender.setCardLastFour(authResult.cardLastFour());
            tender.setCardBrand(authResult.cardBrand());
            tender.setAuthCode(authResult.authCode());
            tender.setRrn(authResult.rrn());
            tender.setStatus(TenderStatus.AUTHORIZED);
            
            preAuthTenders.add(paymentTenderRepository.save(tender));
        }
        transaction.setPaymentTenders(preAuthTenders);

        transaction.setTotalAmountPaid(BigDecimal.ZERO);
        transaction.setStatus(TransactionStatus.AUTHORIZED);
        transaction.setAuthorizedAt(LocalDateTime.now());

        transaction = transactionRepository.save(transaction);

        // Log audit
        createAuditLog(transaction, actorId, actorRole, "PRE_AUTH",
                TransactionStatus.PENDING, TransactionStatus.AUTHORIZED, null);

        log.info("Pre-auth transaction completed: {}", transaction.getTransactionId());
        return mapToResponseDTO(transaction);
    }

    @Transactional
    public TransactionResponseDTO capturePreAuth(String transactionId, CaptureRequestDTO request, String actorId, String actorRole) {
        POSTransaction transaction = transactionRepository.findByTransactionIdForUpdate(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction not found: " + transactionId));

        TransactionType businessType = transaction.getBusinessTransactionType() == null
                ? TransactionType.PURCHASE : transaction.getBusinessTransactionType();
        String idempotencyKey = request.getIdempotencyKey().trim();
        var priorCapture = captureRepository.findByIdempotencyKey(idempotencyKey);
        if (priorCapture.isPresent()) {
            POSTransactionCapture existingCapture = priorCapture.get();
            if (!existingCapture.getTransaction().getTransactionId().equals(transactionId)
                    || !existingCapture.getAmount().equals(request.getCaptureAmount())) {
                throw new IdempotencyKeyExistsException("Capture idempotency key was already used for a different request");
            }
            return mapToResponseDTO(transaction);
        }
        if ((transaction.getTransactionType() != TransactionType.PRE_AUTH
                    && transaction.getStatus() != TransactionStatus.PARTIALLY_CAPTURED)
                || (businessType != TransactionType.SALE && businessType != TransactionType.PURCHASE)) {
            throw new InvalidTransactionStateException("Transaction is not a pre-auth transaction");
        }

        if (transaction.getPreauthHoldAmount() == null) {
            throw new InvalidTransactionStateException("Transaction has no pre-auth hold");
        }
        if (Boolean.TRUE.equals(transaction.getManualCaptureOccurred())) {
            throw new InvalidTransactionStateException("A manual capture has already closed this authorization");
        }
        BigDecimal previouslyCaptured = valueOrZero(transaction.getTotalAmountPaid());
        if (transaction.getStatus() != TransactionStatus.AUTHORIZED
                && transaction.getStatus() != TransactionStatus.PARTIALLY_CAPTURED) {
            throw new InvalidTransactionStateException("Pre-auth transaction must be authorized or partially captured");
        }

        if (transaction.getPreauthExpiresAt() == null || !transaction.getPreauthExpiresAt().isAfter(LocalDateTime.now())) {
            expirePreAuthLocked(transaction, LocalDateTime.now());
            return mapToResponseDTO(transactionRepository.save(transaction));
        }

        BigDecimal requestedCaptureAmount = request.getCaptureAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal remainingHold = transaction.getPreauthHoldAmount().subtract(previouslyCaptured);
        if (Boolean.TRUE.equals(transaction.getWithholdingVatApplied())
                && requestedCaptureAmount.compareTo(remainingHold) != 0) {
            throw new IllegalArgumentException("Withholding VAT pre-authorizations must be captured for the full remaining cash/card balance");
        }
        if (requestedCaptureAmount.compareTo(remainingHold) > 0) {
            throw new InsufficientFundsException("Capture amount exceeds the remaining pre-auth hold");
        }

        TransactionStatus previousStatus = transaction.getStatus();
        GatewayOperationResult gatewayResult = preAuthorizationOperations.capture(
                transaction, requestedCaptureAmount, idempotencyKey);
        BigDecimal cumulativeCaptureAmount = previouslyCaptured.add(requestedCaptureAmount);
        transaction.setManualCaptureOccurred(true);
        transaction.setTotalAmountPaid(cumulativeCaptureAmount);
        applyTenderCaptureAmount(transaction, cumulativeCaptureAmount);
        POSTransactionCapture capture = new POSTransactionCapture();
        capture.setTransaction(transaction);
        capture.setOperationType(CaptureOperationType.MANUAL);
        capture.setIdempotencyKey(idempotencyKey);
        capture.setAmount(requestedCaptureAmount);
        capture.setCumulativeAmount(cumulativeCaptureAmount);
        capture.setProviderReference(gatewayResult.providerReference());
        capture.setProviderStatus(gatewayResult.status());
        captureRepository.save(capture);

        boolean fullyCaptured = cumulativeCaptureAmount.compareTo(transaction.getPreauthHoldAmount()) == 0;
        transaction.setStatus(fullyCaptured ? TransactionStatus.CAPTURED : TransactionStatus.PARTIALLY_CAPTURED);
        transaction.setCapturedAt(transaction.getCapturedAt() == null ? LocalDateTime.now() : transaction.getCapturedAt());
        BigDecimal unusedHold = transaction.getPreauthHoldAmount().subtract(cumulativeCaptureAmount);
        if (unusedHold.signum() > 0) {
            GatewayOperationResult releaseResult = preAuthorizationOperations.release(
                    transaction, unusedHold, idempotencyKey + ":release");
            transaction.setPreauthReleaseReference(releaseResult.providerReference());
            transaction.setPreauthReleaseStatus(releaseResult.status());
        }
        finalizeCapturedPreAuth(transaction, businessType, cumulativeCaptureAmount);
        createAuditLog(transaction, actorId, actorRole, "MANUAL_CAPTURE",
                previousStatus, transaction.getStatus(), "amount=" + requestedCaptureAmount + "; key=" + idempotencyKey);
        return mapToResponseDTO(transactionRepository.save(transaction));
    }

    @Transactional
    public TransactionResponseDTO expirePreAuth(String transactionId, LocalDateTime now) {
        POSTransaction transaction = transactionRepository.findByTransactionIdForUpdate(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction not found: " + transactionId));
        if ((transaction.getStatus() != TransactionStatus.AUTHORIZED
                && transaction.getStatus() != TransactionStatus.PARTIALLY_CAPTURED)
                || Boolean.TRUE.equals(transaction.getManualCaptureOccurred())
                || transaction.getPreauthExpiresAt() == null
                || transaction.getPreauthExpiresAt().isAfter(now)) {
            return mapToResponseDTO(transaction);
        }
        expirePreAuthLocked(transaction, now);
        return mapToResponseDTO(transactionRepository.save(transaction));
    }

    @Transactional
    public TransactionResponseDTO cancelPreAuth(String transactionId, ReversalRequestDTO request, String actorId, String actorRole) {
        POSTransaction transaction = transactionRepository.findByTransactionIdForUpdate(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction not found: " + transactionId));
        String idempotencyKey = request.getIdempotencyKey().trim();
        if (transaction.getStatus() == TransactionStatus.CANCELLED) {
            if (idempotencyKey.equals(transaction.getPreauthCancellationKey())) return mapToResponseDTO(transaction);
            throw new IdempotencyKeyExistsException("Authorization cancellation was already completed with a different idempotency key");
        }
        if (transaction.getTransactionType() != TransactionType.PRE_AUTH
                || transaction.getStatus() != TransactionStatus.AUTHORIZED
                || (transaction.getTotalAmountPaid() != null
                    && transaction.getTotalAmountPaid().compareTo(BigDecimal.ZERO) > 0)) {
            throw new InvalidTransactionStateException("Only an uncaptured authorized pre-authorization can be cancelled");
        }

        TransactionStatus previousStatus = transaction.getStatus();
        BigDecimal holdAmount = valueOrZero(transaction.getPreauthHoldAmount());
        GatewayOperationResult releaseResult = preAuthorizationOperations.release(transaction, holdAmount, idempotencyKey);
        transaction.setStatus(TransactionStatus.CANCELLED);
        transaction.setCancellationReason(request.getReason());
        transaction.setPreauthCancellationKey(idempotencyKey);
        transaction.setPreauthReleaseReference(releaseResult.providerReference());
        transaction.setPreauthReleaseStatus(releaseResult.status());
        createAuditLog(transaction, actorId, actorRole, "VOID_AUTHORIZATION",
                previousStatus, transaction.getStatus(), request.getReason() + "; key=" + idempotencyKey
                        + "; providerReference=" + releaseResult.providerReference()
                        + "; providerStatus=" + releaseResult.status());
        return mapToResponseDTO(transactionRepository.save(transaction));
    }

    private void expirePreAuthLocked(POSTransaction transaction, LocalDateTime now) {
        if (Boolean.TRUE.equals(transaction.getManualCaptureOccurred())) {
            return;
        }
        TransactionStatus previousStatus = transaction.getStatus();
        BigDecimal capturedAmount = valueOrZero(transaction.getTotalAmountPaid());
        BigDecimal holdAmount = valueOrZero(transaction.getPreauthHoldAmount());
        boolean hasManualCapture = Boolean.TRUE.equals(transaction.getManualCaptureOccurred());
        BigDecimal automaticAmount = hasManualCapture || transaction.getAutoCaptureAmount() == null
                ? BigDecimal.ZERO : transaction.getAutoCaptureAmount();
        String expiryKey = "auto-expiry:" + transaction.getTransactionId();
        GatewayOperationResult releaseResult = null;

        if (automaticAmount.signum() > 0 && capturedAmount.signum() == 0) {
            GatewayOperationResult gatewayResult = preAuthorizationOperations.capture(transaction, automaticAmount, expiryKey);
            capturedAmount = automaticAmount;
            transaction.setTotalAmountPaid(capturedAmount);
            applyTenderCaptureAmount(transaction, capturedAmount);
            POSTransactionCapture capture = new POSTransactionCapture();
            capture.setTransaction(transaction);
            capture.setOperationType(CaptureOperationType.AUTO_EXPIRY);
            capture.setIdempotencyKey(expiryKey);
            capture.setAmount(automaticAmount);
            capture.setCumulativeAmount(capturedAmount);
            capture.setProviderReference(gatewayResult.providerReference());
            capture.setProviderStatus(gatewayResult.status());
            captureRepository.save(capture);
            BigDecimal releaseAmount = holdAmount.subtract(capturedAmount).max(BigDecimal.ZERO);
            if (releaseAmount.signum() > 0) {
                releaseResult = preAuthorizationOperations.release(
                        transaction, releaseAmount, expiryKey + ":release");
            }
        } else {
            BigDecimal releaseAmount = holdAmount.subtract(capturedAmount).max(BigDecimal.ZERO);
            if (releaseAmount.signum() > 0) {
                releaseResult = preAuthorizationOperations.release(transaction, releaseAmount, expiryKey);
            }
        }
        if (releaseResult != null) {
            transaction.setPreauthReleaseReference(releaseResult.providerReference());
            transaction.setPreauthReleaseStatus(releaseResult.status());
        }

        transaction.setPreauthExpiredAt(now);
        if (capturedAmount.signum() > 0) {
            TransactionType businessType = transaction.getBusinessTransactionType() == null
                    ? TransactionType.PURCHASE : transaction.getBusinessTransactionType();
            transaction.setStatus(TransactionStatus.CAPTURED);
            transaction.setCapturedAt(transaction.getCapturedAt() == null ? now : transaction.getCapturedAt());
            finalizeCapturedPreAuth(transaction, businessType, capturedAmount);
        } else {
            transaction.setStatus(TransactionStatus.EXPIRED);
        }
        createAuditLog(transaction, "SYSTEM", "SYSTEM", automaticAmount.signum() > 0 && !hasManualCapture
                        ? "AUTO_CAPTURE_AT_EXPIRY" : "EXPIRE_PRE_AUTH",
                previousStatus, transaction.getStatus(),
                "captured=" + capturedAmount + "; released=" + holdAmount.subtract(capturedAmount).max(BigDecimal.ZERO)
                        + (releaseResult == null ? "" : "; providerReference=" + releaseResult.providerReference()
                        + "; providerStatus=" + releaseResult.status()));
    }

    private void finalizeCapturedPreAuth(POSTransaction transaction, TransactionType businessType, BigDecimal capturedAmount) {
        applyCapturedAmounts(transaction, capturedAmount);
        transaction.setTransactionType(businessType);
        transaction.setBusinessTransactionType(businessType);
        transaction.setRefundableAmount(capturedAmount);
        if (transaction.getBankTransactionId() == null) transaction.setBankTransactionId(generateBankTransactionId());
        FiscalReceiptResult fiscalResult = fiscalReceiptService.generateFiscalReceipt(transaction);
        transaction.setFiscalSignature(fiscalResult.fiscalSignature());
        transaction.setFiscalReference(fiscalResult.fiscalReference());
        itemManagementService.applySuccessfulTransaction(transaction);
    }

    private void applyTenderCaptureAmount(POSTransaction transaction, BigDecimal cumulativeCaptureAmount) {
        if (transaction.getPaymentTenders() == null) return;
        BigDecimal remainingCapture = cumulativeCaptureAmount;
        for (POSPaymentTender tender : transaction.getPaymentTenders()) {
            BigDecimal authorizedAmount = valueOrZero(tender.getAmountTendered());
            BigDecimal applied = remainingCapture.min(authorizedAmount);
            tender.setAmountApplied(applied);
            tender.setStatus(applied.compareTo(authorizedAmount) == 0 ? TenderStatus.CAPTURED : TenderStatus.AUTHORIZED);
            remainingCapture = remainingCapture.subtract(applied);
        }
        if (remainingCapture.signum() > 0) {
            throw new InsufficientFundsException("Capture amount exceeds the authorized tender total");
        }
    }

    @Transactional
    public TransactionResponseDTO processRefund(String transactionId, RefundRequestDTO request, String actorId, String actorRole) {
        log.info("Processing refund for transaction: {}", transactionId);

        POSTransaction originalTransaction = transactionRepository.findByTransactionIdForUpdate(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException("Original transaction not found: " + transactionId));

        var priorRequest = transactionRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (priorRequest.isPresent()) {
            POSTransaction existingRefund = priorRequest.get();
            if (existingRefund.getTransactionType() == TransactionType.REFUND
                    && transactionId.equals(existingRefund.getParentTransactionId())
                    && request.getRefundAmount().equals(existingRefund.getNetAmount())) {
                return mapToResponseDTO(existingRefund);
            }
            throw new IdempotencyKeyExistsException("Idempotency key was already used for a different transaction");
        }

        TransactionType businessType = originalTransaction.getBusinessTransactionType() == null
                ? originalTransaction.getTransactionType()
                : originalTransaction.getBusinessTransactionType();
        if (businessType != TransactionType.SALE) {
            throw new InvalidTransactionStateException(
                    "Refunds are only available for Sale transactions. Purchases cannot be refunded from this workflow");
        }

        if (originalTransaction.getStatus() != TransactionStatus.COMPLETED
                && originalTransaction.getStatus() != TransactionStatus.SETTLED
                && originalTransaction.getStatus() != TransactionStatus.CAPTURED
                && originalTransaction.getStatus() != TransactionStatus.PARTIALLY_CAPTURED
                && originalTransaction.getStatus() != TransactionStatus.PARTIALLY_REFUNDED) {
            throw new InvalidTransactionStateException("Can only refund transactions with a confirmed captured balance");
        }

        // Check refund window
        if (originalTransaction.getCreatedAt().plusDays(refundWindowDays).isBefore(LocalDateTime.now())) {
            throw new RefundWindowExpiredException("Refund window has expired");
        }

        // Calculate remaining refundable amount
        BigDecimal totalRefunded = transactionRepository.sumRefundedAmountByParentTransactionId(transactionId);
        if (totalRefunded == null) totalRefunded = BigDecimal.ZERO;

        BigDecimal capturedAmount = valueOrZero(originalTransaction.getTotalAmountPaid());
        BigDecimal recordedRefundableAmount = originalTransaction.getRefundableAmount() == null
                ? capturedAmount : originalTransaction.getRefundableAmount();
        BigDecimal refundableAmount = capturedAmount.min(recordedRefundableAmount);
        BigDecimal previouslyRefunded = totalRefunded.max(valueOrZero(originalTransaction.getRefundedAmount()));
        BigDecimal remainingRefundable = refundableAmount.subtract(previouslyRefunded).max(BigDecimal.ZERO);

        if (refundableAmount.signum() <= 0) {
            throw new InvalidTransactionStateException("Transaction has no confirmed captured amount available to refund");
        }

        if (request.getRefundAmount().compareTo(remainingRefundable) > 0) {
            throw new RefundAmountExceededException(
                    "Refund amount exceeds remaining refundable balance",
                    request.getRefundAmount(),
                    remainingRefundable,
                    transactionId
            );
        }

        // Calculate proportional VAT
        VATCalculationService.RefundCalculation refundCalc = vatCalculationService.calculateRefundWithVat(
                originalTransaction.getGrossAmount(),
                originalTransaction.getVatAmount(),
                valueOrZero(originalTransaction.getServiceFeeAmount()),
                originalTransaction.getNetAmount(),
                request.getRefundAmount()
        );

        // Create refund transaction
        POSTransaction refundTransaction = new POSTransaction();
        refundTransaction.setTransactionId(generateTransactionId());
        refundTransaction.setParentTransactionId(transactionId);
        refundTransaction.setStatus(TransactionStatus.REFUND_PENDING);
        refundTransaction.setTransactionType(TransactionType.REFUND);
        refundTransaction.setCashRegisterId(originalTransaction.getCashRegisterId());
        refundTransaction.setCashierId(actorId);
        refundTransaction.setIdempotencyKey(request.getIdempotencyKey());
        refundTransaction.setCurrencyCode(originalTransaction.getCurrencyCode());
        refundTransaction.setPricesIncludeVat(originalTransaction.getPricesIncludeVat());
        refundTransaction.setCustomerFeeApplied(Boolean.TRUE.equals(originalTransaction.getCustomerFeeApplied()));
        refundTransaction.setCustomerFeeRate(valueOrZero(originalTransaction.getCustomerFeeRate()));
        refundTransaction.setGrossAmount(refundCalc.refundGrossAmount());
        refundTransaction.setVatAmount(refundCalc.refundVatAmount());
        refundTransaction.setServiceFeeAmount(refundCalc.refundServiceFeeAmount());
        refundTransaction.setNetAmount(refundCalc.refundAmountDue());
        refundTransaction.setCustomerName(originalTransaction.getCustomerName());
        refundTransaction.setBefaPaymentId("REFUND-" + refundTransaction.getTransactionId());
        refundTransaction.setBankTransactionId(generateBankTransactionId());
        refundTransaction.setRefundableAmount(BigDecimal.ZERO);
        refundTransaction.setRefundedAmount(request.getRefundAmount());
        refundTransaction.setCancellationReason(request.getReason());
        refundTransaction.setTotalAmountPaid(BigDecimal.ZERO);

        refundTransaction = transactionRepository.save(refundTransaction);

        // Generate fiscal receipt (same flow as PURCHASE)
        FiscalReceiptResult fiscalResult = fiscalReceiptService.generateFiscalReceipt(refundTransaction);
        refundTransaction.setFiscalSignature(fiscalResult.fiscalSignature());
        refundTransaction.setFiscalReference(fiscalResult.fiscalReference());

        // Process refund through payment gateway
        // (In production, this would call the payment gateway to process the refund)
        refundTransaction.setStatus(TransactionStatus.REFUNDED);
        refundTransaction.setSettledAt(LocalDateTime.now());
        refundTransaction = transactionRepository.save(refundTransaction);

        // Update original transaction status if fully refunded
        BigDecimal updatedRefundedAmount = previouslyRefunded.add(request.getRefundAmount());
        if (updatedRefundedAmount.compareTo(refundableAmount) >= 0) {
            originalTransaction.setStatus(TransactionStatus.REFUNDED);
            originalTransaction.setRefundedAmount(updatedRefundedAmount);
            transactionRepository.save(originalTransaction);
        } else {
            originalTransaction.setStatus(TransactionStatus.PARTIALLY_REFUNDED);
            originalTransaction.setRefundedAmount(updatedRefundedAmount);
            transactionRepository.save(originalTransaction);
        }

        // Log audit
        createAuditLog(refundTransaction, actorId, actorRole, "REFUND", 
                TransactionStatus.REFUND_PENDING, TransactionStatus.REFUNDED, request.getReason());

        log.info("Refund transaction completed: {}", refundTransaction.getTransactionId());
        return mapToResponseDTO(refundTransaction);
    }

    @Transactional
    public TransactionResponseDTO processReversal(String transactionId, ReversalRequestDTO request, String actorId, String actorRole) {
        log.info("Processing reversal for transaction: {}", transactionId);

        POSTransaction transaction = transactionRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction not found: " + transactionId));

        if (transaction.getStatus() != TransactionStatus.PENDING
                || transaction.getSettledAt() != null
                || transaction.getCapturedAt() != null) {
            throw new SettledTransactionException("Technical reversal is only available for an unsettled pending transaction; cancel an authorization or refund a capture");
        }

        if (transaction.getStatus() == TransactionStatus.PARTIALLY_REFUNDED) {
            throw new PartialRefundExistsException("Cannot reverse transaction with partial refunds");
        }

        // Check idempotency
        transactionRepository.findByIdempotencyKey(request.getIdempotencyKey())
                .ifPresent(existing -> {
                    throw new IdempotencyKeyExistsException("Transaction with this idempotency key already exists");
                });

        // Create reversal transaction
        POSTransaction reversalTransaction = new POSTransaction();
        reversalTransaction.setTransactionId(generateTransactionId());
        reversalTransaction.setParentTransactionId(transactionId);
        reversalTransaction.setStatus(TransactionStatus.REVERSAL_PENDING);
        reversalTransaction.setTransactionType(TransactionType.REVERSAL);
        reversalTransaction.setCashRegisterId(transaction.getCashRegisterId());
        reversalTransaction.setCashierId(actorId);
        reversalTransaction.setIdempotencyKey(request.getIdempotencyKey());
        reversalTransaction.setGrossAmount(transaction.getGrossAmount());
        reversalTransaction.setVatAmount(transaction.getVatAmount());
        reversalTransaction.setServiceFeeAmount(transaction.getServiceFeeAmount());
        reversalTransaction.setNetAmount(transaction.getNetAmount());
        reversalTransaction.setBefaPaymentId(transaction.getBefaPaymentId());
        reversalTransaction.setBankTransactionId(generateBankTransactionId());
        reversalTransaction.setCancellationReason(request.getReason());
        reversalTransaction.setCurrencyCode(transaction.getCurrencyCode());
        reversalTransaction.setPricesIncludeVat(transaction.getPricesIncludeVat());
        reversalTransaction.setCustomerFeeApplied(Boolean.TRUE.equals(transaction.getCustomerFeeApplied()));
        reversalTransaction.setCustomerFeeRate(valueOrZero(transaction.getCustomerFeeRate()));
        reversalTransaction.setTotalAmountPaid(BigDecimal.ZERO);

        reversalTransaction = transactionRepository.save(reversalTransaction);

        // Generate fiscal receipt (same flow as PURCHASE)
        FiscalReceiptResult fiscalResult = fiscalReceiptService.generateFiscalReceipt(reversalTransaction);
        reversalTransaction.setFiscalSignature(fiscalResult.fiscalSignature());
        reversalTransaction.setFiscalReference(fiscalResult.fiscalReference());

        // Reverse original transaction
        transaction.setStatus(TransactionStatus.REVERSED);
        transactionRepository.save(transaction);

        reversalTransaction.setStatus(TransactionStatus.REVERSED);
        reversalTransaction.setSettledAt(LocalDateTime.now());
        reversalTransaction = transactionRepository.save(reversalTransaction);

        // Log audit
        createAuditLog(reversalTransaction, actorId, actorRole, "REVERSAL", 
                TransactionStatus.REVERSAL_PENDING, TransactionStatus.REVERSED, request.getReason());

        log.info("Reversal transaction completed: {}", reversalTransaction.getTransactionId());
        return mapToResponseDTO(reversalTransaction);
    }

    @Transactional(readOnly = true)
    public TransactionResponseDTO getTransaction(String transactionId) {
        POSTransaction transaction = transactionRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction not found: " + transactionId));
        return mapToResponseDTO(transaction);
    }

    private void validateCashRegisterAndCashier(String cashRegisterId, String cashierId) {
        // In production, this would check against a cash register service
        // For now, we'll assume the register is open and cashier is assigned
        log.debug("Validating cash register {} and cashier {}", cashRegisterId, cashierId);
    }

    private void validatePaymentTenders(List<PaymentTenderDTO> tenders, BigDecimal netAmount, boolean isPreAuth) {
        BigDecimal totalApplied = tenders.stream()
                .map(PaymentTenderDTO::getAmountTendered)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalApplied.compareTo(netAmount) < 0) {
            throw new InsufficientPaymentException("Total payment amount is less than transaction amount");
        }

        // Skip exact amount validation for pre-authorizations
        if (!isPreAuth) {
            for (PaymentTenderDTO tender : tenders) {
                if (tender.getPaymentMethod() != PaymentMethod.CASH &&
                    !tender.getAmountTendered().equals(tender.getAmountApplied())) {
                    throw new ExactAmountRequiredException(
                        String.format("Non-cash payments must be exact amounts. Tendered: %s, Applied: %s",
                            tender.getAmountTendered(), tender.getAmountApplied()));
                }
            }
        }
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String requireCurrentCurrency(String requestedCurrency) {
        String configuredCurrency = configurationService.getCurrencyCode();
        if (requestedCurrency != null && !requestedCurrency.equalsIgnoreCase(configuredCurrency)) {
            throw new IllegalArgumentException("Transaction currency must match the configured currency " + configuredCurrency);
        }
        return configuredCurrency;
    }

    private PaymentGatewayResult processPaymentAuthorization(PaymentTenderDTO tender) {
        // In production, this would call the actual payment gateway
        // For now, we'll simulate a successful authorization
        return new PaymentGatewayResult(
                "AUTH-" + UUID.randomUUID().toString().substring(0, 8),
                "RRN-" + UUID.randomUUID().toString().substring(0, 8),
                "****" + UUID.randomUUID().toString().substring(0, 4),
                "VISA"
        );
    }

    private void createAuditLog(POSTransaction transaction, String actorId, String actorRole, 
            String action, TransactionStatus fromStatus, TransactionStatus toStatus, String reason) {
        POSAuditLog auditLog = new POSAuditLog();
        auditLog.setTransaction(transaction);
        auditLog.setActorId(actorId);
        auditLog.setActorRole(actorRole);
        auditLog.setAction(action);
        auditLog.setFromStatus(fromStatus);
        auditLog.setToStatus(toStatus);
        auditLog.setReason(reason);
        auditLogRepository.save(auditLog);
    }

    private String generateTransactionId() {
        return "TXN-" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + 
               "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private TransactionResponseDTO mapToResponseDTO(POSTransaction transaction) {
        List<LineItemResponseDTO> lineItems = transaction.getLineItems() != null 
                ? transaction.getLineItems().stream()
                        .map(li -> new LineItemResponseDTO(
                                li.getId(),
                                li.getItemCode(),
                                li.getItemName(),
                                li.getUnitPrice(),
                                li.getQuantity(),
                                li.getLineGrossAmount(),
                                li.getLineVatAmount(),
                                li.getLineServiceFeeAmount(),
                                li.getLineNetAmount(),
                                li.getDiscountAmount(),
                                li.getVatExempt(),
                                li.getVatCategory()
                        ))
                        .toList()
                : List.of();

        List<PaymentTenderResponseDTO> paymentTenders = transaction.getPaymentTenders() != null
                ? transaction.getPaymentTenders().stream()
                        .map(pt -> new PaymentTenderResponseDTO(
                                pt.getId(),
                                pt.getPaymentMethod(),
                                pt.getPaymentProvider(),
                                pt.getAmountTendered(),
                                pt.getAmountApplied(),
                                pt.getCashChange(),
                                pt.getCardLastFour(),
                                pt.getCardBrand(),
                                pt.getAuthCode(),
                                pt.getRrn(),
                                pt.getStatus()
                        ))
                        .toList()
                : List.of();

        TransactionResponseDTO response = new TransactionResponseDTO();
        response.setTransactionId(transaction.getTransactionId());
        response.setBefaPaymentId(transaction.getBefaPaymentId());
        response.setIdempotencyKey(transaction.getIdempotencyKey());
        response.setBankTransactionId(transaction.getBankTransactionId());
        response.setCashRegisterId(transaction.getCashRegisterId());
        response.setCashierId(transaction.getCashierId());
        response.setCustomerName(transaction.getCustomerName());
        response.setCustomerTin(transaction.getCustomerTin());
        response.setStatus(transaction.getStatus());
        response.setTransactionType(transaction.getTransactionType());
        response.setBusinessTransactionType(transaction.getBusinessTransactionType());
        response.setCurrencyCode(transaction.getCurrencyCode());
        response.setPricesIncludeVat(transaction.getPricesIncludeVat());
        response.setCustomerFeeApplied(transaction.getCustomerFeeApplied());
        response.setCustomerFeeRate(transaction.getCustomerFeeRate());
        response.setGrossAmount(transaction.getGrossAmount());
        response.setVatAmount(transaction.getVatAmount());
        response.setServiceFeeAmount(transaction.getServiceFeeAmount());
        response.setNetAmount(transaction.getNetAmount());
        response.setWithholdingVatApplied(transaction.getWithholdingVatApplied());
        response.setWithholdingVatRate(transaction.getWithholdingVatRate());
        response.setWithholdingVatAmount(transaction.getWithholdingVatAmount());
        response.setWithholdingCertificateNumber(transaction.getWithholdingCertificateNumber());
        response.setWithholdingCertificateDate(transaction.getWithholdingCertificateDate());
        response.setTotalAmountPaid(transaction.getTotalAmountPaid());
        response.setRefundedAmount(transaction.getRefundedAmount());
        response.setRefundableAmount(transaction.getRefundableAmount());
        response.setFiscalSignature(transaction.getFiscalSignature());
        response.setFiscalReference(transaction.getFiscalReference());
        response.setFiscalSubmissionStatus(transaction.getFiscalSubmissionStatus());
        response.setFiscalSubmissionMode(transaction.getFiscalSubmissionMode());
        response.setFiscalAuthorityResponseCode(transaction.getFiscalAuthorityResponseCode());
        response.setFiscalAuthorityResponseMessage(transaction.getFiscalAuthorityResponseMessage());
        response.setFiscalSubmittedToAuthorityAt(transaction.getFiscalSubmittedToAuthorityAt());
        response.setCreatedAt(transaction.getCreatedAt());
        response.setAuthorizedAt(transaction.getAuthorizedAt());
        response.setSettledAt(transaction.getSettledAt());
        response.setCapturedAt(transaction.getCapturedAt());
        response.setPreauthExpiresAt(transaction.getPreauthExpiresAt());
        response.setPreauthExpiredAt(transaction.getPreauthExpiredAt());
        response.setParentTransactionId(transaction.getParentTransactionId());
        response.setCancellationReason(transaction.getCancellationReason());
        response.setPreauthReleaseReference(transaction.getPreauthReleaseReference());
        response.setPreauthReleaseStatus(transaction.getPreauthReleaseStatus());
        response.setPreauthHoldAmount(transaction.getPreauthHoldAmount());
        response.setAutoCaptureAmount(transaction.getAutoCaptureAmount());
        response.setManualCaptureOccurred(transaction.getManualCaptureOccurred());
        List<POSTransactionCapture> captures = captureRepository.findByTransaction_IdOrderByCreatedAtAsc(transaction.getId());
        response.setCaptures(captures == null ? List.of() : captures.stream()
                .map(capture -> new CaptureOperationResponseDTO(
                        capture.getOperationType(),
                        capture.getIdempotencyKey(),
                        capture.getAmount(),
                        capture.getCumulativeAmount(),
                        capture.getProviderReference(),
                        capture.getProviderStatus(),
                        capture.getCreatedAt()))
                .toList());
        response.setLineItems(lineItems);
        response.setPaymentTenders(paymentTenders);
        return response;
    }

    public org.springframework.data.domain.Page<PendingTransactionDTO> getPendingTransactions(
            LocalDateTime dateFrom,
            LocalDateTime dateTo,
            TransactionType businessTransactionType,
            org.springframework.data.domain.Pageable pageable) {
        log.info("Fetching pending transactions with date range: {} to {}, business type: {}, page: {}, size: {}",
                dateFrom, dateTo, businessTransactionType, pageable.getPageNumber(), pageable.getPageSize());
        if (dateFrom != null && dateTo != null) {
            return transactionRepository.findPendingTransactionSummariesBetween(
                    dateFrom, dateTo, businessTransactionType, pageable);
        }
        if (dateFrom != null) {
            return transactionRepository.findPendingTransactionSummariesFrom(
                    dateFrom, businessTransactionType, pageable);
        }
        if (dateTo != null) {
            return transactionRepository.findPendingTransactionSummariesTo(
                    dateTo, businessTransactionType, pageable);
        }
        return transactionRepository.findPendingTransactionSummaries(businessTransactionType, pageable);
    }

    private record PaymentGatewayResult(String authCode, String rrn, String cardLastFour, String cardBrand) {}
}
