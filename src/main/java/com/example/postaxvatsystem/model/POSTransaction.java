package com.example.postaxvatsystem.model;

import com.example.postaxvatsystem.listener.POSTransactionListener;
import com.example.postaxvatsystem.security.EncryptedStringConverter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "pos_transactions")
@EntityListeners(POSTransactionListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class POSTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pos_transaction_seq")
    @SequenceGenerator(name = "pos_transaction_seq", sequenceName = "pos_transaction_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String transactionId;

    @Column(name = "parent_transaction_id", length = 100)
    private String parentTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_transaction_type", length = 20)
    private TransactionType businessTransactionType;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "ETB";

    @Column(name = "prices_include_vat", nullable = false)
    private Boolean pricesIncludeVat = false;

    @Column(name = "customer_fee_applied", nullable = false)
    private Boolean customerFeeApplied = false;

    @Column(name = "customer_fee_rate", nullable = false, precision = 10, scale = 6)
    private BigDecimal customerFeeRate = BigDecimal.ZERO;

    @Column(nullable = false, length = 36)
    private String cashRegisterId;

    @Column(nullable = false, length = 36)
    private String cashierId;

    @Column(nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "befa_payment_id", nullable = false, unique = true, length = 100)
    private String befaPaymentId;

    @Column(name = "bank_transaction_id", length = 100)
    private String bankTransactionId;

    @Column(nullable = false)
    private BigDecimal grossAmount;

    @Column(nullable = false)
    private BigDecimal vatAmount;

    @Column(nullable = false)
    private BigDecimal serviceFeeAmount;

    @Column(nullable = false)
    private BigDecimal netAmount;

    @Column(nullable = false)
    private BigDecimal totalAmountPaid;

    @Column(name = "withholding_vat_applied", nullable = false)
    private Boolean withholdingVatApplied = false;

    @Column(name = "withholding_vat_rate", nullable = false, precision = 10, scale = 6)
    private BigDecimal withholdingVatRate = BigDecimal.ZERO;

    @Column(name = "withholding_vat_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal withholdingVatAmount = BigDecimal.ZERO;

    @Column(name = "withholding_certificate_number", length = 120)
    private String withholdingCertificateNumber;

    @Column(name = "withholding_certificate_date")
    private java.time.LocalDate withholdingCertificateDate;

    @Column(length = 500)
    private String customerName;

    @Column(length = 500)
    @Convert(converter = EncryptedStringConverter.class)
    private String customerTin;

    @Column(name = "customer_tin_hash", length = 255)
    private String customerTinHash;

    @Column(length = 500)
    private String fiscalSignature;

    @Column(length = 100)
    private String fiscalReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "fiscal_submission_status", length = 20)
    private TaxSubmissionStatus fiscalSubmissionStatus;

    @Column(name = "fiscal_submission_mode", length = 20)
    private String fiscalSubmissionMode;

    @Column(name = "fiscal_authority_response_code", length = 50)
    private String fiscalAuthorityResponseCode;

    @Column(name = "fiscal_authority_response_message", length = 255)
    private String fiscalAuthorityResponseMessage;

    @Column(name = "fiscal_submitted_to_authority_at")
    private LocalDateTime fiscalSubmittedToAuthorityAt;

    @Column
    private BigDecimal preauthHoldAmount;

    @Column
    private LocalDateTime preauthExpiresAt;

    @Column(name = "auto_capture_amount", nullable = false)
    private BigDecimal autoCaptureAmount = BigDecimal.ZERO;

    @Column(name = "manual_capture_occurred", nullable = false)
    private Boolean manualCaptureOccurred = false;

    @Column(name = "preauth_expired_at")
    private LocalDateTime preauthExpiredAt;

    @Column
    private LocalDateTime settledAt;

    @Column
    private LocalDateTime authorizedAt;

    @Column
    private LocalDateTime capturedAt;

    @Column(length = 255)
    private String cancellationReason;

    @Column(name = "preauth_cancellation_key", length = 120)
    private String preauthCancellationKey;

    @Column(name = "preauth_release_reference", length = 120)
    private String preauthReleaseReference;

    @Column(name = "preauth_release_status", length = 40)
    private String preauthReleaseStatus;

    @Column
    private BigDecimal refundableAmount;

    @Column
    private BigDecimal refundedAmount;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @JsonIgnoreProperties("transaction")
    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<POSTransactionLineItem> lineItems;

    @JsonIgnoreProperties("transaction")
    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<POSPaymentTender> paymentTenders;

    @JsonIgnoreProperties("transaction")
    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL)
    private List<POSAuditLog> auditLogs;

    @JsonIgnoreProperties("transaction")
    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL)
    private List<POSTransactionCapture> captures;

}
