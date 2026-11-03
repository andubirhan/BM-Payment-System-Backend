package com.example.postaxvatsystem.service;

import com.example.postaxvatsystem.dto.LineItemDTO;
import com.example.postaxvatsystem.model.VATRate;
import com.example.postaxvatsystem.repository.VATRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VATCalculationServiceTest {

    @Mock
    private VATRateRepository vatRateRepository;

    @Mock
    private POSConfigurationService posConfigurationService;

    @InjectMocks
    private VATCalculationService vatCalculationService;

    @BeforeEach
    void setUp() {
        lenient().when(posConfigurationService.getServiceFeeRate()).thenReturn(new BigDecimal("0.02"));
        lenient().when(vatRateRepository.findByCategoryCode("STANDARD"))
                .thenReturn(Optional.of(rate("STANDARD", "0.15")));
        lenient().when(vatRateRepository.findByCategoryCode("REDUCED"))
                .thenReturn(Optional.of(rate("REDUCED", "0.05")));
    }

    @Test
    void calculatesExclusiveVatWithoutApplyingOptionalFeeByDefault() {
        VATCalculationService.LineItemCalculation result = vatCalculationService.calculateLineItem(
                item("100.00", 2, "STANDARD"), false, false);

        assertMoney("200.00", result.lineGrossAmount());
        assertMoney("30.00", result.lineVatAmount());
        assertMoney("0.00", result.lineServiceFeeAmount());
        assertMoney("230.00", result.lineNetAmount());
    }

    @Test
    void extractsVatFromInclusivePrice() {
        VATCalculationService.LineItemCalculation result = vatCalculationService.calculateLineItem(
                item("115.00", 1, "STANDARD"), true, false);

        assertMoney("100.00", result.lineGrossAmount());
        assertMoney("15.00", result.lineVatAmount());
        assertMoney("115.00", result.lineNetAmount());
    }

    @Test
    void appliesConfiguredFeeOnlyWhenRequested() {
        VATCalculationService.LineItemCalculation result = vatCalculationService.calculateLineItem(
                item("100.00", 1, "STANDARD"), false, true);

        assertMoney("100.00", result.lineGrossAmount());
        assertMoney("15.00", result.lineVatAmount());
        assertMoney("2.00", result.lineServiceFeeAmount());
        assertMoney("117.00", result.lineNetAmount());
    }

    @Test
    void roundsDecimalLineAmountsToTwoPlaces() {
        VATCalculationService.LineItemCalculation result = vatCalculationService.calculateLineItem(
                item("10.10", 1, "REDUCED"), false, false);

        assertMoney("10.10", result.lineGrossAmount());
        assertMoney("0.51", result.lineVatAmount());
        assertMoney("10.61", result.lineNetAmount());
    }

    @Test
    void aggregatesLineCalculationsWithoutLosingRounding() {
        VATCalculationService.TransactionCalculation result = vatCalculationService.calculateTransaction(
                List.of(item("SKU-1", "10.10", 1, "REDUCED"),
                        item("SKU-2", "10.10", 1, "REDUCED")),
                false,
                false);

        assertMoney("20.20", result.totalGrossAmount());
        assertMoney("1.02", result.totalVatAmount());
        assertMoney("21.22", result.totalNetAmount());
        assertEquals(2, result.lineCalculations().size());
    }

    @Test
    void storesLineCalculationsUsingNormalizedItemCodes() {
        VATCalculationService.TransactionCalculation result = vatCalculationService.calculateTransaction(
                List.of(item("electronics-002", "100.00", 1, "STANDARD")),
                false,
                false);

        assertMoney("100.00", result.lineCalculations().get("ELECTRONICS-002").lineGrossAmount());
    }

    @Test
    void preservesEachLineCalculationInRequestOrderWhenItemCodesRepeat() {
        VATCalculationService.TransactionCalculation result = vatCalculationService.calculateTransaction(
                List.of(item("SKU-1", "10.00", 1, "STANDARD"),
                        item("SKU-1", "20.00", 2, "STANDARD")),
                false,
                false);

        assertEquals(2, result.orderedLineCalculations().size());
        assertMoney("10.00", result.orderedLineCalculations().get(0).lineGrossAmount());
        assertMoney("40.00", result.orderedLineCalculations().get(1).lineGrossAmount());
    }

    @Test
    void allocatesRefundAcrossTaxableBaseVatAndFee() {
        VATCalculationService.RefundCalculation result = vatCalculationService.calculateRefundWithVat(
                new BigDecimal("100.00"),
                new BigDecimal("15.00"),
                new BigDecimal("2.00"),
                new BigDecimal("117.00"),
                new BigDecimal("58.50"));

        assertMoney("50.00", result.refundGrossAmount());
        assertMoney("7.50", result.refundVatAmount());
        assertMoney("1.00", result.refundServiceFeeAmount());
        assertMoney("58.50", result.refundAmountDue());
    }

    @Test
    void rejectsUnknownVatCategoriesInsteadOfSilentlyOmittingVat() {
        when(vatRateRepository.findByCategoryCode("UNKNOWN")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> vatCalculationService.calculateLineItem(
                item("10.00", 1, "UNKNOWN"), false, false));
    }

    private VATRate rate(String category, String amount) {
        VATRate rate = new VATRate();
        rate.setCategoryCode(category);
        rate.setCategoryName(category);
        rate.setRate(new BigDecimal(amount));
        rate.setActive(true);
        return rate;
    }

    private LineItemDTO item(String unitPrice, int quantity, String category) {
        return item("SKU-1", unitPrice, quantity, category);
    }

    private LineItemDTO item(String code, String unitPrice, int quantity, String category) {
        return new LineItemDTO(
                code,
                "Test item",
                new BigDecimal(unitPrice),
                quantity,
                BigDecimal.ZERO,
                false,
                category,
                null);
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
