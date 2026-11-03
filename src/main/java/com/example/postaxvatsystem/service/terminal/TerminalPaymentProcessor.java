package com.example.postaxvatsystem.service.terminal;

import com.example.postaxvatsystem.model.POSTransaction;

import java.util.List;

/**
 * Boundary for card-present authorization. A real device adapter should exchange
 * opaque processor tokens and transaction references, never PAN or CVV with this app.
 */
public interface TerminalPaymentProcessor {

    List<TerminalScenarioDTO> availableScenarios();

    TerminalAuthorizationResult authorize(POSTransaction transaction, String scenarioId);
}
