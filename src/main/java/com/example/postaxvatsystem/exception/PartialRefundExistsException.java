package com.example.postaxvatsystem.exception;

public class PartialRefundExistsException extends RuntimeException {
    public PartialRefundExistsException(String message) {
        super(message);
    }
}
