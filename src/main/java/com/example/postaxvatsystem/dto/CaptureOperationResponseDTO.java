package com.example.postaxvatsystem.dto;

import com.example.postaxvatsystem.model.CaptureOperationType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CaptureOperationResponseDTO {
    private CaptureOperationType operationType;
    private String idempotencyKey;
    private BigDecimal amount;
    private BigDecimal cumulativeAmount;
    private String providerReference;
    private String providerStatus;
    private LocalDateTime createdAt;
}
