package com.example.postaxvatsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Standard tracking structure used by the reconciliation engine to map downstream bank outcomes.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BankStatusResponse {
    private String status; // SUCCESS, FAILED, NOT_FOUND
    private String rawResponseJson;
}
