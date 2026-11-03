package com.example.postaxvatsystem.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BankTransactionIdGeneratorTest {

    @Test
    void generatesTimestampedReferencesWithIncrementingSuffixes() {
        BankTransactionIdGenerator generator = new BankTransactionIdGenerator();

        String first = generator.nextId();
        String second = generator.nextId();

        assertTrue(first.matches("BANK-TXN-\\d{8}-\\d{6}-\\d+"));
        assertTrue(second.matches("BANK-TXN-\\d{8}-\\d{6}-\\d+"));
        assertEquals(sequenceOf(first) + 1L, sequenceOf(second));
    }

    private long sequenceOf(String bankTransactionId) {
        return Long.parseLong(bankTransactionId.substring(bankTransactionId.lastIndexOf('-') + 1));
    }
}
