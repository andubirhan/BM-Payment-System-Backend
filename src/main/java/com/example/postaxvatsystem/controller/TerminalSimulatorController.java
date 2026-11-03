package com.example.postaxvatsystem.controller;

import com.example.postaxvatsystem.service.terminal.TerminalPaymentProcessor;
import com.example.postaxvatsystem.service.terminal.TerminalScenarioDTO;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pos/bank-payments/terminal-scenarios")
@Profile("fixture")
public class TerminalSimulatorController {
    private final TerminalPaymentProcessor terminalPaymentProcessor;

    public TerminalSimulatorController(TerminalPaymentProcessor terminalPaymentProcessor) {
        this.terminalPaymentProcessor = terminalPaymentProcessor;
    }

    @GetMapping
    public List<TerminalScenarioDTO> getScenarios() {
        return terminalPaymentProcessor.availableScenarios();
    }
}
