package com.example.postaxvatsystem.service.terminal;

public record TerminalAuthorizationResult(
        Outcome outcome,
        String message,
        String bankTransactionId,
        String authorizationCode,
        String retrievalReferenceNumber,
        String cardBrand,
        String cardLastFour) {

    public enum Outcome {
        APPROVED,
        DECLINED,
        PENDING
    }
}
