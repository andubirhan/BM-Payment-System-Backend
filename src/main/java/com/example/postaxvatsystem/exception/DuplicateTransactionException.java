package com.example.postaxvatsystem.exception;

public class DuplicateTransactionException extends RuntimeException {
    private final String befaPaymentId;
    private final String existingTransactionId;
    private final String status;

    public DuplicateTransactionException(String message, String befaPaymentId, String existingTransactionId, String status) {
        super(message);
        this.befaPaymentId = befaPaymentId;
        this.existingTransactionId = existingTransactionId;
        this.status = status;
    }

    public String getBefaPaymentId() {
        return befaPaymentId;
    }

    public String getExistingTransactionId() {
        return existingTransactionId;
    }

    public String getStatus() {
        return status;
    }
}
