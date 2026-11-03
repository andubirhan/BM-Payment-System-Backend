package com.example.postaxvatsystem.exception;

import java.math.BigDecimal;

public class RefundAmountExceededException extends RuntimeException {
    private final BigDecimal requested;
    private final BigDecimal remainingRefundable;
    private final String originalTransaction;

    public RefundAmountExceededException(String message, BigDecimal requested, BigDecimal remainingRefundable, String originalTransaction) {
        super(message);
        this.requested = requested;
        this.remainingRefundable = remainingRefundable;
        this.originalTransaction = originalTransaction;
    }

    public BigDecimal getRequested() {
        return requested;
    }

    public BigDecimal getRemainingRefundable() {
        return remainingRefundable;
    }

    public String getOriginalTransaction() {
        return originalTransaction;
    }
}
