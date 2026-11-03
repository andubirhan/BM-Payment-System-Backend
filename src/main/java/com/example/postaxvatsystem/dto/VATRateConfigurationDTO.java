package com.example.postaxvatsystem.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class VATRateConfigurationDTO {

    @NotBlank
    @Pattern(regexp = "[A-Z0-9_-]{1,50}")
    private String categoryCode;

    @NotBlank
    private String categoryName;

    @NotNull
    @DecimalMin("0.0000")
    @DecimalMax("1.0000")
    @Digits(integer = 1, fraction = 4)
    private BigDecimal rate;

    @NotNull
    private Boolean active;
}
