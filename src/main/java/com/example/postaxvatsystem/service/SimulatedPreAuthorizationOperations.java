package com.example.postaxvatsystem.service;

import com.example.postaxvatsystem.model.POSTransaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@Slf4j
public class SimulatedPreAuthorizationOperations implements PreAuthorizationOperations {

    @Override
    public GatewayOperationResult capture(POSTransaction transaction, BigDecimal amount, String idempotencyKey) {
        log.info("SIMULATED pre-authorization capture: transaction={}, amount={}, key={}",
                transaction.getTransactionId(), amount, idempotencyKey);
        return new GatewayOperationResult("SIM-CAPTURE-" + UUID.randomUUID(), "SIMULATED_APPROVED");
    }

    @Override
    public GatewayOperationResult release(POSTransaction transaction, BigDecimal amount, String idempotencyKey) {
        log.info("SIMULATED pre-authorization release: transaction={}, amount={}, key={}",
                transaction.getTransactionId(), amount, idempotencyKey);
        return new GatewayOperationResult("SIM-RELEASE-" + UUID.randomUUID(), "SIMULATED_APPROVED");
    }
}
