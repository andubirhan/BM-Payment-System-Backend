package com.example.postaxvatsystem.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PricingConfigurationDTO {

    @NotNull
    @DecimalMin("0.000000")
    @DecimalMax("1.000000")
    @Digits(integer = 1, fraction = 6)
    private BigDecimal serviceFeeRate;

    @NotNull
    @DecimalMin("0.000000")
    @DecimalMax("1.000000")
    @Digits(integer = 1, fraction = 6)
    private BigDecimal saleWithholdingVatRate = BigDecimal.ZERO;

    @NotNull
    @DecimalMin("0.000000")
    @DecimalMax("1.000000")
    @Digits(integer = 1, fraction = 6)
    private BigDecimal purchaseWithholdingVatRate = BigDecimal.ZERO;

    @NotEmpty
    @Valid
    private List<VATRateConfigurationDTO> vatRates;
}
