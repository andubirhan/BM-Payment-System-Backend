package com.example.postaxvatsystem.exception;

public class InvalidTransactionStateException extends RuntimeException {
    public InvalidTransactionStateException(String message) {
        super(message);
    }
}
