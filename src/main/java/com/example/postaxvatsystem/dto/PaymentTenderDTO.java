package com.example.postaxvatsystem.dto;

import com.example.postaxvatsystem.model.PaymentMethod;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTenderDTO {

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    private String paymentProvider;

    @NotNull(message = "Amount tendered is required")
    @DecimalMin(value = "0.01", message = "Amount tendered must be at least 0.01")
    @Digits(integer = 17, fraction = 2, message = "Amount tendered must have no more than two decimal places")
    @JsonProperty("amountTendered")
    private BigDecimal amountTendered;

    @JsonProperty("amountApplied")
    @Digits(integer = 17, fraction = 2, message = "Amount applied must have no more than two decimal places")
    private BigDecimal amountApplied;
}
