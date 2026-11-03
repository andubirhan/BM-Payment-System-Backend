package com.example.postaxvatsystem.dto;

import com.example.postaxvatsystem.model.PaymentMethod;
import com.example.postaxvatsystem.model.TenderStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTenderResponseDTO {

    private Long id;
    private PaymentMethod paymentMethod;
    private String paymentProvider;
    private BigDecimal amountTendered;
    private BigDecimal amountApplied;
    private BigDecimal cashChange;
    private String cardLastFour;
    private String cardBrand;
    @JsonIgnore
    private String authCode;
    @JsonIgnore
    private String rrn;
    private TenderStatus status;
}
