package com.example.postaxvatsystem.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseRequestDTO {

    @NotBlank(message = "Idempotency key is required")
    private String idempotencyKey;

    @NotBlank(message = "BEFA payment ID is required")
    private String befaPaymentId;

    private String bankTransactionId;

    @NotBlank(message = "Cash register ID is required")
    private String cashRegisterId;

    @NotBlank(message = "Cashier ID is required")
    private String cashierId;

    @NotEmpty(message = "At least one line item is required")
    @Valid
    private List<LineItemDTO> lineItems;

    @NotEmpty(message = "At least one payment tender is required")
    @Valid
    private List<PaymentTenderDTO> paymentTenders;

    private String customerName;

    private String customerTin;

    private Boolean pricesIncludeVat = false;

    private Boolean applyCustomerFee = false;

    private Boolean applyWithholdingVat = false;

    private String withholdingCertificateNumber;

    private java.time.LocalDate withholdingCertificateDate;

    private String currencyCode = "ETB";
}
