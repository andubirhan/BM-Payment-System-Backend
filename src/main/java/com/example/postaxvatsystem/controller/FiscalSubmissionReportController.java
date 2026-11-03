package com.example.postaxvatsystem.controller;

import com.example.postaxvatsystem.dto.FiscalSubmissionReportDTO;
import com.example.postaxvatsystem.dto.FiscalSubmissionDetailDTO;
import com.example.postaxvatsystem.model.POSTransaction;
import com.example.postaxvatsystem.model.POSTransactionLineItem;
import com.example.postaxvatsystem.model.TaxSubmissionStatus;
import com.example.postaxvatsystem.model.TaxTransaction;
import com.example.postaxvatsystem.repository.POSTransactionRepository;
import com.example.postaxvatsystem.repository.TaxTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/pos/reports")
@RequiredArgsConstructor
public class FiscalSubmissionReportController {

    private final TaxTransactionRepository taxTransactionRepository;
    private final POSTransactionRepository posTransactionRepository;

    @GetMapping("/fiscal-submissions/{taxTransactionId}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getFiscalSubmissionDetail(@PathVariable String taxTransactionId) {
        var fiscalSubmission = taxTransactionRepository.findByTaxTransactionId(taxTransactionId);
        if (fiscalSubmission.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var posTransaction = posTransactionRepository.findByTransactionId(
                fiscalSubmission.get().getPosTransactionId());
        if (posTransaction.isEmpty()) {
            return ResponseEntity.status(404).body("The fiscal submission exists, but its linked POS transaction was not found.");
        }
        return ResponseEntity.ok(toDetailDTO(fiscalSubmission.get(), posTransaction.get()));
    }

    @GetMapping("/fiscal-submissions")
    public ResponseEntity<?> getFiscalSubmissions(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String mode,
            @RequestParam(required = false) Boolean creditNote,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            return ResponseEntity.badRequest().body("dateFrom must be on or before dateTo");
        }
        if (page < 0 || size < 1 || size > 100) {
            return ResponseEntity.badRequest().body("page must be non-negative and size must be between 1 and 100");
        }

        TaxSubmissionStatus submissionStatus = null;
        if (status != null && !status.isBlank()) {
            try {
                submissionStatus = TaxSubmissionStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                return ResponseEntity.badRequest().body("Unsupported fiscal submission status");
            }
        }

        Specification<TaxTransaction> specification = (root, criteriaQuery, builder) ->
                builder.conjunction();
        if (dateFrom != null) {
            LocalDateTime from = dateFrom;
            specification = specification.and((root, criteriaQuery, builder) ->
                    builder.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (dateTo != null) {
            LocalDateTime to = dateTo;
            specification = specification.and((root, criteriaQuery, builder) ->
                    builder.lessThanOrEqualTo(root.get("createdAt"), to));
        }
        if (submissionStatus != null) {
            TaxSubmissionStatus selectedStatus = submissionStatus;
            specification = specification.and((root, criteriaQuery, builder) ->
                    builder.equal(root.get("submissionStatus"), selectedStatus));
        }
        if (mode != null && !mode.isBlank()) {
            String selectedMode = mode.trim().toUpperCase(Locale.ROOT);
            specification = specification.and((root, criteriaQuery, builder) ->
                    builder.equal(builder.upper(root.get("submissionMode")), selectedMode));
        }
        if (creditNote != null) {
            specification = specification.and((root, criteriaQuery, builder) ->
                    builder.equal(root.get("isCreditNote"), creditNote));
        }
        if (query != null && !query.isBlank()) {
            String search = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, criteriaQuery, builder) ->
                    builder.or(
                            builder.like(builder.lower(root.get("taxTransactionId")), search),
                            builder.like(builder.lower(root.get("posTransactionId")), search),
                            builder.like(builder.lower(root.get("authorityTransactionId")), search),
                            builder.like(builder.lower(root.get("fiscalReference")), search),
                            builder.like(builder.lower(root.get("tin")), search)));
        }

        Page<FiscalSubmissionReportDTO> result = taxTransactionRepository.findAll(
                        specification,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(FiscalSubmissionReportController::toReportDTO);
        return ResponseEntity.ok(result);
    }

    private static FiscalSubmissionReportDTO toReportDTO(TaxTransaction submission) {
        return new FiscalSubmissionReportDTO(
                submission.getTaxTransactionId(),
                submission.getPosTransactionId(),
                submission.getAuthorityTransactionId(),
                submission.getFiscalReference(),
                submission.getSubmissionStatus(),
                submission.getSubmissionMode(),
                submission.getGrossAmount(),
                submission.getVatAmount(),
                submission.getServiceFeeAmount(),
                submission.getNetAmount(),
                submission.getCurrencyCode(),
                submission.getTin(),
                submission.getTerminalId(),
                submission.getSubmittedToAuthorityAt(),
                submission.getAuthorityResponseCode(),
                submission.getAuthorityResponseMessage(),
                submission.getIsCreditNote(),
                submission.getOriginalTransactionId(),
                submission.getCreatedAt());
    }

    private static FiscalSubmissionDetailDTO toDetailDTO(
            TaxTransaction submission,
            POSTransaction transaction) {
        var lineItems = transaction.getLineItems() == null
                ? java.util.List.<FiscalSubmissionDetailDTO.FiscalReceiptLineItemDTO>of()
                : transaction.getLineItems().stream()
                .map(FiscalSubmissionReportController::toLineItemDTO)
                .toList();
        return new FiscalSubmissionDetailDTO(
                toReportDTO(submission),
                transaction.getTransactionType(),
                transaction.getBusinessTransactionType(),
                transaction.getCustomerName(),
                transaction.getCustomerTin(),
                transaction.getPricesIncludeVat(),
                transaction.getWithholdingVatAmount(),
                lineItems);
    }

    private static FiscalSubmissionDetailDTO.FiscalReceiptLineItemDTO toLineItemDTO(
            POSTransactionLineItem lineItem) {
        return new FiscalSubmissionDetailDTO.FiscalReceiptLineItemDTO(
                lineItem.getItemCode(),
                lineItem.getItemName(),
                lineItem.getItemDescription(),
                lineItem.getQuantity(),
                lineItem.getUnitPrice(),
                lineItem.getDiscountAmount(),
                lineItem.getVatCategory(),
                lineItem.getVatExempt(),
                lineItem.getVatRate(),
                lineItem.getLineGrossAmount(),
                lineItem.getLineVatAmount(),
                lineItem.getLineServiceFeeAmount(),
                lineItem.getLineNetAmount());
    }
}
