package com.example.postaxvatsystem.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.example.postaxvatsystem.model.TransactionType;

/**
 * Request payload sent from Postman/Browser to initiate a bank payment transaction.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentInitiationRequest {
    @NotBlank(message = "BEFA payment reference sequence cannot be blank")
    private String befaPaymentId;

    @NotBlank(message = "Cash register ID is required")
    private String cashRegisterId;

    @NotBlank(message = "Cashier ID is required")
    private String cashierId;

    private TransactionType transactionType = TransactionType.PURCHASE;

    private String terminalScenarioId;

    private Boolean pricesIncludeVat = false;

    private Boolean applyCustomerFee = false;

    private Boolean applyWithholdingVat = false;

    private String withholdingCertificateNumber;

    private java.time.LocalDate withholdingCertificateDate;

    private String currencyCode = "ETB";
}
