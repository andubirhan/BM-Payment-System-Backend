package com.example.postaxvatsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class ItemReportDTO {
    private String itemCode;
    private String itemName;
    private Long currentQuantityOnHand;
    private Long saleQuantity;
    private Long purchaseQuantity;
    private BigDecimal saleAmount;
    private BigDecimal purchaseAmount;
    private Long saleTransactionCount;
    private Long purchaseTransactionCount;
}
