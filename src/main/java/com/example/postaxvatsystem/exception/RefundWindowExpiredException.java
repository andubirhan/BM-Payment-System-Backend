package com.example.postaxvatsystem.exception;

public class RefundWindowExpiredException extends RuntimeException {
    public RefundWindowExpiredException(String message) {
        super(message);
    }
}
