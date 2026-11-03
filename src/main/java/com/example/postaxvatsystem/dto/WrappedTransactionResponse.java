package com.example.postaxvatsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WrappedTransactionResponse {
    private int statusCode;
    private Map<String, String> headers;
    private TransactionResponseDTO body;
}
