package com.example.postaxvatsystem.service;

import com.example.postaxvatsystem.dto.PricingConfigurationDTO;
import com.example.postaxvatsystem.dto.VATRateConfigurationDTO;
import com.example.postaxvatsystem.model.VATRate;
import com.example.postaxvatsystem.repository.VATRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PricingConfigurationService {

    private final VATRateRepository vatRateRepository;
    private final POSConfigurationService posConfigurationService;

    @Transactional
    public PricingConfigurationDTO getConfiguration() {
        PricingConfigurationDTO result = new PricingConfigurationDTO();
        result.setServiceFeeRate(posConfigurationService.getServiceFeeRate());
        result.setSaleWithholdingVatRate(posConfigurationService.getSaleWithholdingVatRate());
        result.setPurchaseWithholdingVatRate(posConfigurationService.getPurchaseWithholdingVatRate());
        result.setVatRates(vatRateRepository.findAll().stream()
                .map(this::toDto)
                .toList());
        return result;
    }

    @Transactional
    public PricingConfigurationDTO updateConfiguration(PricingConfigurationDTO request) {
        List<VATRateConfigurationDTO> rates = request.getVatRates();
        Set<String> categoryCodes = new HashSet<>();
        for (VATRateConfigurationDTO rate : rates) {
            if (!categoryCodes.add(rate.getCategoryCode())) {
                throw new IllegalArgumentException("VAT category codes must be unique");
            }
        }
        if (rates.stream().noneMatch(rate -> Boolean.TRUE.equals(rate.getActive()))) {
            throw new IllegalArgumentException("At least one VAT rate must remain active");
        }

        Set<String> submittedCodes = rates.stream()
                .map(VATRateConfigurationDTO::getCategoryCode)
                .collect(Collectors.toSet());
        List<VATRate> existingRates = vatRateRepository.findAll();
        for (VATRate existing : existingRates) {
            if (!submittedCodes.contains(existing.getCategoryCode()) && Boolean.TRUE.equals(existing.getActive())) {
                existing.setActive(false);
            }
        }
        vatRateRepository.saveAll(existingRates);

        rates.forEach(rate -> {
            VATRate entity = vatRateRepository.findByCategoryCode(rate.getCategoryCode())
                    .orElseGet(VATRate::new);
            boolean changed = entity.getId() == null
                    || entity.getRate() == null
                    || entity.getRate().compareTo(rate.getRate()) != 0;
            entity.setCategoryCode(rate.getCategoryCode());
            entity.setCategoryName(rate.getCategoryName().trim());
            entity.setRate(rate.getRate());
            entity.setActive(rate.getActive());
            if (changed) {
                entity.setEffectiveFrom(LocalDateTime.now());
                entity.setEffectiveTo(null);
            }
            vatRateRepository.save(entity);
        });

        posConfigurationService.updateServiceFeeRate(request.getServiceFeeRate());
        posConfigurationService.updateSaleWithholdingVatRate(request.getSaleWithholdingVatRate());
        posConfigurationService.updatePurchaseWithholdingVatRate(request.getPurchaseWithholdingVatRate());
        PricingConfigurationDTO result = new PricingConfigurationDTO();
        result.setServiceFeeRate(posConfigurationService.getServiceFeeRate());
        result.setSaleWithholdingVatRate(posConfigurationService.getSaleWithholdingVatRate());
        result.setPurchaseWithholdingVatRate(posConfigurationService.getPurchaseWithholdingVatRate());
        result.setVatRates(vatRateRepository.findAll().stream().map(this::toDto).toList());
        return result;
    }

    private VATRateConfigurationDTO toDto(VATRate entity) {
        VATRateConfigurationDTO result = new VATRateConfigurationDTO();
        result.setCategoryCode(entity.getCategoryCode());
        result.setCategoryName(entity.getCategoryName());
        result.setRate(entity.getRate());
        result.setActive(entity.getActive());
        return result;
    }
}
