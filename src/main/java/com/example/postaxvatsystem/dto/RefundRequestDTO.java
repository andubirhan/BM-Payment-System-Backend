package com.example.postaxvatsystem.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefundRequestDTO {

    @NotBlank(message = "Idempotency key is required")
    private String idempotencyKey;

    @NotNull(message = "Refund amount is required")
    @DecimalMin(value = "0.01", message = "Refund amount must be at least 0.01")
    @Digits(integer = 17, fraction = 2, message = "Refund amount must have no more than two decimal places")
    private BigDecimal refundAmount;

    private List<String> lineItemIds; // For partial refunds

    private String reason;
}
