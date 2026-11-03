package com.example.postaxvatsystem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CurrencyConfigurationDTO {

    @NotBlank
    @Pattern(regexp = "^[A-Za-z]{3}$", message = "Currency code must be a three-letter ISO code")
    private String currencyCode;
}
