package com.example.postaxvatsystem.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Audit trail entity for bank payment processing.
 * Ensures financial compliance with immutable append-only ledger.
 * 
 * Per BRD SEC-3.1: This table must be strictly configured with append-only access privileges.
 * Database update or delete commands against this table space must trigger security alerts.
 */
@Entity
@Table(name = "bank_payment_audit_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BankPaymentAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private POSTransaction transaction;

    /**
     * Enum: BANK_SUBMISSION_INTENT, SUCCESS, FAILED
     */
    @Column(nullable = false, length = 50)
    private String actionType;

    /**
     * Tracking Enum: PENDING, SUCCESS, FAILED
     */
    @Column(nullable = false, length = 30)
    private String status;

    /**
     * Contains the raw data fetched from BEFA-Private.
     * Per BRD SEC-1.1: Must be AES-256-GCM encrypted at application layer before storage.
     */
    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String requestPayload;

    /**
     * Stores the raw response from the bank for audit analysis.
     * Per BRD SEC-1.1: Must be AES-256-GCM encrypted at application layer before storage.
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String responsePayload;

    /**
     * Non-updatable timestamp tracking exact operational execution.
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
