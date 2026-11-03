package com.example.postaxvatsystem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReversalRequestDTO {

    @NotBlank(message = "Idempotency key is required")
    @Size(max = 120, message = "Idempotency key cannot exceed 120 characters")
    private String idempotencyKey;

    private String reason;
}
