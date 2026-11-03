package com.example.postaxvatsystem.service;

import com.example.postaxvatsystem.dto.LineItemDTO;
import com.example.postaxvatsystem.model.VATRate;
import com.example.postaxvatsystem.repository.VATRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VATCalculationService {

    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final int MONEY_SCALE = 2;

    private final VATRateRepository vatRateRepository;
    private final POSConfigurationService posConfigurationService;

    public BigDecimal getServiceFeeRate() {
        return posConfigurationService.getServiceFeeRate();
    }

    public LineItemCalculation calculateLineItem(LineItemDTO lineItem) {
        return calculateLineItem(lineItem, false, false);
    }

    public LineItemCalculation calculateLineItem(
            LineItemDTO lineItem,
            boolean pricesIncludeVat,
            boolean applyCustomerFee) {
        BigDecimal enteredSubtotal = lineItem.getUnitPrice()
                .multiply(BigDecimal.valueOf(lineItem.getQuantity()))
                .subtract(valueOrZero(lineItem.getDiscountAmount()))
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (enteredSubtotal.signum() < 0) {
            throw new IllegalArgumentException("Discount cannot exceed the line item subtotal");
        }

        BigDecimal vatRate = getVatRate(lineItem);
        BigDecimal taxableBase;
        BigDecimal vatAmount;
        if (Boolean.TRUE.equals(lineItem.getVatExempt()) || vatRate.signum() == 0) {
            taxableBase = enteredSubtotal;
            vatAmount = ZERO;
        } else if (pricesIncludeVat) {
            vatAmount = enteredSubtotal.multiply(vatRate)
                    .divide(BigDecimal.ONE.add(vatRate), MONEY_SCALE, RoundingMode.HALF_UP);
            taxableBase = enteredSubtotal.subtract(vatAmount).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        } else {
            taxableBase = enteredSubtotal;
            vatAmount = taxableBase.multiply(vatRate).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }

        BigDecimal customerFee = applyCustomerFee
                ? taxableBase.multiply(getServiceFeeRate()).setScale(MONEY_SCALE, RoundingMode.HALF_UP)
                : ZERO;
        BigDecimal amountDue = taxableBase.add(vatAmount).add(customerFee)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        return new LineItemCalculation(taxableBase, vatAmount, customerFee, amountDue, vatRate);
    }

    public TransactionCalculation calculateTransaction(List<LineItemDTO> lineItems) {
        return calculateTransaction(lineItems, false, false);
    }

    public TransactionCalculation calculateTransaction(
            List<LineItemDTO> lineItems,
            boolean pricesIncludeVat,
            boolean applyCustomerFee) {
        BigDecimal totalGrossAmount = ZERO;
        BigDecimal totalVatAmount = ZERO;
        BigDecimal totalServiceFeeAmount = ZERO;
        BigDecimal totalAmountDue = ZERO;
        Map<String, LineItemCalculation> calculations = new HashMap<>();
        List<LineItemCalculation> orderedCalculations = new java.util.ArrayList<>(lineItems.size());

        for (LineItemDTO lineItem : lineItems) {
            LineItemCalculation calculation = calculateLineItem(lineItem, pricesIncludeVat, applyCustomerFee);
            orderedCalculations.add(calculation);
            String itemCode = lineItem.getItemCode() == null
                    ? null : lineItem.getItemCode().trim().toUpperCase(Locale.ROOT);
            calculations.put(itemCode, calculation);
            totalGrossAmount = totalGrossAmount.add(calculation.lineGrossAmount());
            totalVatAmount = totalVatAmount.add(calculation.lineVatAmount());
            totalServiceFeeAmount = totalServiceFeeAmount.add(calculation.lineServiceFeeAmount());
            totalAmountDue = totalAmountDue.add(calculation.lineNetAmount());
        }

        return new TransactionCalculation(
                money(totalGrossAmount),
                money(totalVatAmount),
                money(totalServiceFeeAmount),
                money(totalAmountDue),
                calculations,
                List.copyOf(orderedCalculations)
        );
    }

    public RefundCalculation calculateRefundWithVat(
            BigDecimal originalGrossAmount,
            BigDecimal originalVatAmount,
            BigDecimal originalServiceFeeAmount,
            BigDecimal originalAmountDue,
            BigDecimal refundAmount) {
        if (originalAmountDue.signum() <= 0 || refundAmount.signum() <= 0) {
            throw new IllegalArgumentException("Original amount and refund amount must be positive");
        }
        BigDecimal ratio = refundAmount.divide(originalAmountDue, 12, RoundingMode.HALF_UP);
        BigDecimal refundGross = money(originalGrossAmount.multiply(ratio));
        BigDecimal refundVat = money(originalVatAmount.multiply(ratio));
        BigDecimal refundFee = money(originalServiceFeeAmount.multiply(ratio));
        BigDecimal allocationDifference = refundAmount
                .subtract(refundGross.add(refundVat).add(refundFee));
        refundGross = money(refundGross.add(allocationDifference));

        BigDecimal vatRate = originalGrossAmount.signum() == 0
                ? BigDecimal.ZERO
                : originalVatAmount.divide(originalGrossAmount, 10, RoundingMode.HALF_UP);
        return new RefundCalculation(refundGross, refundVat, refundFee, money(refundAmount), vatRate);
    }

    public RefundCalculation calculateRefundWithVat(
            BigDecimal originalGrossAmount,
            BigDecimal originalVatAmount,
            BigDecimal refundAmount) {
        return calculateRefundWithVat(
                originalGrossAmount,
                originalVatAmount,
                ZERO,
                originalGrossAmount.add(originalVatAmount),
                refundAmount
        );
    }

    private BigDecimal getVatRate(LineItemDTO lineItem) {
        if (Boolean.TRUE.equals(lineItem.getVatExempt())) {
            return BigDecimal.ZERO;
        }
        String categoryCode = lineItem.getVatCategory();
        if (categoryCode == null || categoryCode.isBlank()) {
            throw new IllegalArgumentException("A VAT category is required for taxable items");
        }
        VATRate rate = vatRateRepository.findByCategoryCode(categoryCode)
                .filter(VATRate::getActive)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No active VAT rate is configured for category " + categoryCode));
        return rate.getRate();
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public record LineItemCalculation(
            BigDecimal lineGrossAmount,
            BigDecimal lineVatAmount,
            BigDecimal lineServiceFeeAmount,
            BigDecimal lineNetAmount,
            BigDecimal vatRate
    ) {}

    public record TransactionCalculation(
            BigDecimal totalGrossAmount,
            BigDecimal totalVatAmount,
            BigDecimal totalServiceFeeAmount,
            BigDecimal totalNetAmount,
            Map<String, LineItemCalculation> lineCalculations,
            List<LineItemCalculation> orderedLineCalculations
    ) {}

    public record RefundCalculation(
            BigDecimal refundGrossAmount,
            BigDecimal refundVatAmount,
            BigDecimal refundServiceFeeAmount,
            BigDecimal refundAmountDue,
            BigDecimal vatRate
    ) {}
}
