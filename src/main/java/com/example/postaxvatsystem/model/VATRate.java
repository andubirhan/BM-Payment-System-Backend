package com.example.postaxvatsystem.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "vat_rates")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VATRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String categoryCode;

    @Column(nullable = false, length = 255)
    private String categoryName;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal rate; // VAT rate as decimal (e.g., 0.15 for 15%)

    @Column(nullable = false)
    private Boolean active;

    @Column(length = 255)
    private String description;

    @Column
    private LocalDateTime effectiveFrom;

    @Column
    private LocalDateTime effectiveTo;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
