package com.example.postaxvatsystem.fixture;

import com.example.postaxvatsystem.model.POSTransaction;
import com.example.postaxvatsystem.service.terminal.TerminalAuthorizationResult;
import com.example.postaxvatsystem.service.terminal.TerminalPaymentProcessor;
import com.example.postaxvatsystem.service.terminal.TerminalScenarioDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Component
@Profile("fixture")
public class FixtureTerminalPaymentProcessor implements TerminalPaymentProcessor {
    private final List<Scenario> scenarios;

    public FixtureTerminalPaymentProcessor(
            ObjectMapper objectMapper,
            @Value("classpath:fixtures/terminal-payment-scenarios.json") Resource scenarioResource) {
        try (var input = scenarioResource.getInputStream()) {
            scenarios = Arrays.asList(objectMapper.readValue(input, Scenario[].class));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load terminal payment scenarios.", exception);
        }
        Set<String> ids = new HashSet<>();
        for (Scenario scenario : scenarios) {
            if (scenario.id() == null || scenario.id().isBlank() || !ids.add(scenario.id())) {
                throw new IllegalStateException("Terminal scenario IDs must be present and unique.");
            }
            if (scenario.delayMillis() < 0 || scenario.delayMillis() > 5000) {
                throw new IllegalStateException("Terminal scenario delays must be between 0 and 5000 milliseconds.");
            }
            if (scenario.outcome() == null || scenario.cardBrand() == null || scenario.cardLastFour() == null
                    || !scenario.cardLastFour().matches("\\d{4}")) {
                throw new IllegalStateException("Terminal scenarios require a valid outcome and four display digits.");
            }
        }
    }

    @Override
    public List<TerminalScenarioDTO> availableScenarios() {
        return scenarios.stream()
                .map(scenario -> new TerminalScenarioDTO(
                        scenario.id(), scenario.label(), scenario.description()))
                .toList();
    }

    @Override
    public TerminalAuthorizationResult authorize(POSTransaction transaction, String scenarioId) {
        Scenario scenario = scenarios.stream()
                .filter(candidate -> candidate.id().equalsIgnoreCase(scenarioId == null ? "" : scenarioId.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Select a valid terminal test scenario."));
        try {
            Thread.sleep(scenario.delayMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new TerminalAuthorizationResult(
                    TerminalAuthorizationResult.Outcome.PENDING,
                    "Terminal response was interrupted; check transaction status before retrying.",
                    null, null, null, scenario.cardBrand(), scenario.cardLastFour());
        }

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        String reference = "SIM-" + suffix;
        return new TerminalAuthorizationResult(
                scenario.outcome(),
                scenario.message(),
                scenario.outcome() == TerminalAuthorizationResult.Outcome.APPROVED ? "SIM-BANK-" + suffix : null,
                scenario.outcome() == TerminalAuthorizationResult.Outcome.APPROVED ? "SIM-AUTH-" + suffix : null,
                scenario.outcome() == TerminalAuthorizationResult.Outcome.APPROVED ? reference : null,
                scenario.cardBrand(),
                scenario.cardLastFour());
    }

    private record Scenario(
            String id,
            String label,
            String description,
            String message,
            TerminalAuthorizationResult.Outcome outcome,
            long delayMillis,
            String cardBrand,
            String cardLastFour) {
    }
}
