package com.example.postaxvatsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Represents data fetched from the internal BEFA-Private secure repository
 * before initiating bank payment processing.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BefaPaymentDataDTO {
    private String befaPaymentId;
    private String idempotencyKey;
    private String cashRegisterId;
    private String cashierId;
    private String customerTin;
    private String customerName;
    private BigDecimal amountInCents;
    private List<LineItem> lineItems;
    private List<PaymentTender> paymentTenders;
    private Map<String, String> headers;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineItem {
        private String itemCode;
        private String itemName;
        private BigDecimal unitPrice;
        private Long quantity;
        private BigDecimal discountAmount;
        private Boolean vatExempt;
        private String vatCategory;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentTender {
        private String paymentMethod;
        private String paymentProvider;
        private BigDecimal amountTendered;
    }
}
