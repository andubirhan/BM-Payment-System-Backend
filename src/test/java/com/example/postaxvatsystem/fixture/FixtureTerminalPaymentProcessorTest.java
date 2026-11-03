package com.example.postaxvatsystem.fixture;

import com.example.postaxvatsystem.service.terminal.TerminalScenarioDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FixtureTerminalPaymentProcessorTest {

    @Test
    void exposesConfiguredScenariosWithoutCardholderData() throws Exception {
        FixtureTerminalPaymentProcessor processor = new FixtureTerminalPaymentProcessor(
                new ObjectMapper(), new ClassPathResource("fixtures/terminal-payment-scenarios.json"));

        List<TerminalScenarioDTO> scenarios = processor.availableScenarios();
        String response = new ObjectMapper().writeValueAsString(scenarios).toLowerCase();

        assertEquals(List.of("APPROVED", "DECLINED", "PENDING"),
                scenarios.stream().map(TerminalScenarioDTO::id).toList());
        assertFalse(response.contains("\"pan\""));
        assertFalse(response.contains("\"cvv\""));
        assertFalse(response.contains("\"cardnumber\""));
    }
}
