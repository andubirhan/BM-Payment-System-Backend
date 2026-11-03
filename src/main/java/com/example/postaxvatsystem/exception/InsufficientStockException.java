package com.example.postaxvatsystem.exception;

public class InsufficientStockException extends RuntimeException {
    private final String itemCode;
    private final Long available;
    private final Long required;

    public InsufficientStockException(String itemCode, Long available, Long required) {
        super(String.format("Insufficient stock for item %s. Available: %d, Required: %d", itemCode, available, required));
        this.itemCode = itemCode;
        this.available = available;
        this.required = required;
    }

    public String getItemCode() {
        return itemCode;
    }

    public Long getAvailable() {
        return available;
    }

    public Long getRequired() {
        return required;
    }
}
