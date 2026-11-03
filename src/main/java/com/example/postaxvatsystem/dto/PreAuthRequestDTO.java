package com.example.postaxvatsystem.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import com.example.postaxvatsystem.model.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PreAuthRequestDTO {

    @NotBlank(message = "Idempotency key is required")
    private String idempotencyKey;

    @NotBlank(message = "BEFA payment ID is required")
    private String befaPaymentId;

    private String bankTransactionId;

    @NotBlank(message = "Cash register ID is required")
    private String cashRegisterId;

    @NotBlank(message = "Cashier ID is required")
    private String cashierId;

    @NotNull(message = "Pre-auth hold amount is required")
    @DecimalMin(value = "0.01", message = "Pre-auth hold amount must be at least 0.01")
    @Digits(integer = 17, fraction = 2, message = "Pre-auth hold amount must have no more than two decimal places")
    private BigDecimal preauthHoldAmount;

    @NotEmpty(message = "At least one payment tender is required")
    @Valid
    private List<PaymentTenderDTO> paymentTenders;

    private String customerName;

    private String customerTin;

    private TransactionType businessTransactionType;

    @Valid
    private List<LineItemDTO> lineItems;

    @Min(value = 1, message = "Expiry hours must be at least 1")
    private Integer expiresInHours;

    @DecimalMin(value = "0.00", message = "Automatic capture amount cannot be negative")
    @Digits(integer = 17, fraction = 2, message = "Automatic capture amount must have no more than two decimal places")
    private BigDecimal autoCaptureAmount = BigDecimal.ZERO;

    private Boolean pricesIncludeVat = false;

    private Boolean applyCustomerFee = false;

    private Boolean applyWithholdingVat = false;

    private String withholdingCertificateNumber;

    private java.time.LocalDate withholdingCertificateDate;

    private String currencyCode = "ETB";
}
