package com.example.postaxvatsystem.dto;

import com.example.postaxvatsystem.model.TransactionStatus;
import com.example.postaxvatsystem.model.TransactionType;
import com.example.postaxvatsystem.model.TaxSubmissionStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponseDTO {

    private String transactionId;
    private String befaPaymentId;
    private String idempotencyKey;
    private String bankTransactionId;
    private String cashRegisterId;
    private String cashierId;
    private String customerName;
    private String customerTin;
    private TransactionStatus status;
    private TransactionType transactionType;
    private TransactionType businessTransactionType;
    private String currencyCode;
    private Boolean pricesIncludeVat;
    private Boolean customerFeeApplied;
    private BigDecimal customerFeeRate;
    private BigDecimal grossAmount;
    private BigDecimal vatAmount;
    private BigDecimal serviceFeeAmount;
    private BigDecimal netAmount;
    private Boolean withholdingVatApplied;
    private BigDecimal withholdingVatRate;
    private BigDecimal withholdingVatAmount;
    private String withholdingCertificateNumber;
    private java.time.LocalDate withholdingCertificateDate;
    private BigDecimal totalAmountPaid;
    private BigDecimal refundedAmount;
    private BigDecimal refundableAmount;
    private String fiscalSignature;
    private String fiscalReference;
    private TaxSubmissionStatus fiscalSubmissionStatus;
    private String fiscalSubmissionMode;
    private String fiscalAuthorityResponseCode;
    private String fiscalAuthorityResponseMessage;
    private LocalDateTime fiscalSubmittedToAuthorityAt;
    private LocalDateTime createdAt;
    private LocalDateTime authorizedAt;
    private LocalDateTime settledAt;
    private LocalDateTime capturedAt;
    private LocalDateTime preauthExpiresAt;
    private LocalDateTime preauthExpiredAt;
    private String parentTransactionId;
    private String cancellationReason;
    private String preauthReleaseReference;
    private String preauthReleaseStatus;
    private BigDecimal preauthHoldAmount;
    private BigDecimal autoCaptureAmount;
    private Boolean manualCaptureOccurred;
    private List<CaptureOperationResponseDTO> captures;
    private List<LineItemResponseDTO> lineItems;
    private List<PaymentTenderResponseDTO> paymentTenders;
}
