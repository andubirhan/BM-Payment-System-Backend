package com.example.postaxvatsystem.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pos_transaction_captures")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class POSTransactionCapture {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnoreProperties({"captures", "lineItems", "paymentTenders", "auditLogs"})
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private POSTransaction transaction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CaptureOperationType operationType;

    @Column(nullable = false, unique = true, length = 120)
    private String idempotencyKey;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private BigDecimal cumulativeAmount;

    @Column(name = "provider_reference", length = 120)
    private String providerReference;

    @Column(name = "provider_status", length = 40)
    private String providerStatus;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
