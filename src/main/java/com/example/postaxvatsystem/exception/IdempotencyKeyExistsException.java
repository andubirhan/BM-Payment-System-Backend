package com.example.postaxvatsystem.exception;

public class IdempotencyKeyExistsException extends RuntimeException {
    public IdempotencyKeyExistsException(String message) {
        super(message);
    }
}
