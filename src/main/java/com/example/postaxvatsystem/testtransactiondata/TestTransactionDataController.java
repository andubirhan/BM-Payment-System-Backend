package com.example.postaxvatsystem.testtransactiondata;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/test-transaction-data")
@RequiredArgsConstructor
@Profile("fixture")
public class TestTransactionDataController {

    private final TestTransactionDataService testTransactionDataService;

    @GetMapping(value = "/{transactionType}", produces = MediaType.APPLICATION_JSON_VALUE)
    public JsonNode load(@PathVariable String transactionType, Authentication authentication) {
        return testTransactionDataService.load(transactionType, username(authentication));
    }

    @PutMapping(value = "/{transactionType}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public JsonNode save(@PathVariable String transactionType, @RequestBody JsonNode data,
                         Authentication authentication) {
        return testTransactionDataService.save(transactionType, username(authentication), data);
    }

    private String username(Authentication authentication) {
        return authentication == null ? null : authentication.getName();
    }
}
