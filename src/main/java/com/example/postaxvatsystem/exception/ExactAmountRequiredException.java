package com.example.postaxvatsystem.exception;

public class ExactAmountRequiredException extends RuntimeException {
    public ExactAmountRequiredException(String message) {
        super(message);
    }
}
