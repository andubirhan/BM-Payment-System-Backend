package com.example.postaxvatsystem.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tax_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaxTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String taxTransactionId;

    @Column(nullable = false, length = 100)
    private String posTransactionId;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "ETB";

    @Column(name = "authority_transaction_id", length = 100)
    private String authorityTransactionId;

    @Column(name = "submission_mode", nullable = false, length = 20)
    private String submissionMode = "LEGACY";

    @Column(nullable = false)
    private BigDecimal grossAmount;

    @Column(nullable = false)
    private BigDecimal vatAmount;

    @Column(nullable = false)
    private BigDecimal serviceFeeAmount;

    @Column(nullable = false)
    private BigDecimal netAmount;

    @Column(length = 500)
    private String fiscalSignature;

    @Column(length = 100)
    private String fiscalReference;

    @Column(length = 50)
    private String tin; // Tax Identification Number

    @Column(length = 255)
    private String companyName;

    @Column(length = 255)
    private String branchName;

    @Column(length = 100)
    private String terminalId;

    @Column
    private LocalDateTime submittedToAuthorityAt;

    @Column(length = 20)
    private String authorityResponseCode;

    @Column(length = 255)
    private String authorityResponseMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaxSubmissionStatus submissionStatus;

    @Column
    private Boolean isCreditNote;

    @Column(length = 100)
    private String originalTransactionId; // For credit notes

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
