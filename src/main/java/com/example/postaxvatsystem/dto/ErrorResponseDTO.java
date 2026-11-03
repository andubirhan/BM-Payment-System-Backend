package com.example.postaxvatsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponseDTO {

    private String code;
    private String message;
    private Map<String, Object> details;
    private Boolean retryable;
    private LocalDateTime timestamp;
}
