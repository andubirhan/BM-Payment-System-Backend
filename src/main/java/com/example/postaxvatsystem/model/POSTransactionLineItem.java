package com.example.postaxvatsystem.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pos_transaction_line_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class POSTransactionLineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private POSTransaction transaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id")
    @JsonIgnore
    private InventoryItem inventoryItem;

    @Column(nullable = false, length = 100)
    private String itemCode;

    @Column(nullable = false, length = 255)
    private String itemName;

    @Column(nullable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private BigDecimal lineGrossAmount;

    @Column(nullable = false)
    private BigDecimal lineVatAmount;

    @Column(nullable = false)
    private BigDecimal lineServiceFeeAmount;

    @Column(nullable = false)
    private BigDecimal lineNetAmount;

    @Column(nullable = false)
    private BigDecimal discountAmount;

    @Column(nullable = false)
    private Boolean vatExempt;

    @Column(length = 50)
    private String vatCategory;

    @Column(nullable = false)
    private BigDecimal vatRate;

    @Column(length = 255)
    private String itemDescription;

    @Column(name = "inventory_adjusted", nullable = false)
    @JsonIgnore
    private Boolean inventoryAdjusted = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
