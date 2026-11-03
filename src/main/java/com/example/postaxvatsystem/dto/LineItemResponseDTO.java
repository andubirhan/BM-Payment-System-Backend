package com.example.postaxvatsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LineItemResponseDTO {

    private Long id;
    private String itemCode;
    private String itemName;
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal lineGrossAmount;
    private BigDecimal lineVatAmount;
    private BigDecimal lineServiceFeeAmount;
    private BigDecimal lineNetAmount;
    private BigDecimal discountAmount;
    private Boolean vatExempt;
    private String vatCategory;
}
