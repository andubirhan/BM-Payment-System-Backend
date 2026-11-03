package com.example.postaxvatsystem.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Digits;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LineItemDTO {

    @NotBlank(message = "Item code is required")
    private String itemCode;

    @NotBlank(message = "Item name is required")
    private String itemName;

    @NotNull(message = "Unit price is required")
    @Digits(integer = 17, fraction = 2, message = "Unit price must have no more than two decimal places")
    @Min(value = 0, message = "Unit price must be positive")
    private BigDecimal unitPrice;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    @Min(value = 0, message = "Discount amount must be positive")
    @Digits(integer = 17, fraction = 2, message = "Discount amount must have no more than two decimal places")
    private BigDecimal discountAmount;

    private Boolean vatExempt = false;

    private String vatCategory;

    private String itemDescription;
}
