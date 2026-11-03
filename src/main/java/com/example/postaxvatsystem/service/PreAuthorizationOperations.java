package com.example.postaxvatsystem.service;

import com.example.postaxvatsystem.model.POSTransaction;

import java.math.BigDecimal;

public interface PreAuthorizationOperations {
    GatewayOperationResult capture(POSTransaction transaction, BigDecimal amount, String idempotencyKey);
    GatewayOperationResult release(POSTransaction transaction, BigDecimal amount, String idempotencyKey);
}
