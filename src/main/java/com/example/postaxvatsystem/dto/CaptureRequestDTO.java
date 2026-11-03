package com.example.postaxvatsystem.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CaptureRequestDTO {

    @NotBlank(message = "Idempotency key is required")
    @Size(max = 120, message = "Idempotency key cannot exceed 120 characters")
    private String idempotencyKey;

    @NotNull(message = "Capture amount is required")
    @DecimalMin(value = "0.01", message = "Capture amount must be at least 0.01")
    @Digits(integer = 17, fraction = 2, message = "Capture amount must have no more than two decimal places")
    private BigDecimal captureAmount;
}
