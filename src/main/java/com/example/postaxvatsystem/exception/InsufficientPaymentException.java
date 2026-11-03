package com.example.postaxvatsystem.exception;

public class InsufficientPaymentException extends RuntimeException {
    public InsufficientPaymentException(String message) {
        super(message);
    }
}
