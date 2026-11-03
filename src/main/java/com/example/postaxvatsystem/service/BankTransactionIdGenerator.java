package com.example.postaxvatsystem.service;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class BankTransactionIdGenerator {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final AtomicLong SEQUENCE = new AtomicLong();

    public String nextId() {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        return "BANK-TXN-" + timestamp + "-" + SEQUENCE.incrementAndGet();
    }
}
