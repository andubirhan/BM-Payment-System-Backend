package com.example.postaxvatsystem.model;

import com.example.postaxvatsystem.security.EncryptedStringConverter;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pos_payment_tenders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class POSPaymentTender {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


//    @JsonBackReference // Stops the infinite JSON loop and stops LAZY loading crashes
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private POSTransaction transaction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod paymentMethod;

    @Column(length = 50)
    private String paymentProvider;

    @Column(nullable = false)
    private BigDecimal amountTendered;

    @Column(nullable = false)
    private BigDecimal amountApplied;

    @Column
    private BigDecimal cashChange;

    @Column(length = 8)
    private String cardLastFour;

    @Column(length = 50)
    private String cardBrand;

    @Column(length = 500)
    @Convert(converter = EncryptedStringConverter.class)
    private String authCode;

    @Column(length = 500)
    @Convert(converter = EncryptedStringConverter.class)
    private String rrn; // Retrieval Reference Number

    @Column(length = 100)
    private String transactionReference;

    @Enumerated(EnumType.STRING)
    private TenderStatus status;

    @Column(length = 255)
    private String errorMessage;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
