package com.example.postaxvatsystem.exception;

public class PreAuthExpiredException extends RuntimeException {
    public PreAuthExpiredException(String message) {
        super(message);
    }
}
