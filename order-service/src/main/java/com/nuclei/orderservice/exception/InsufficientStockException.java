package com.nuclei.orderservice.exception;

public class InsufficientStockException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InsufficientStockException(final Long productId) {
        super("Insufficient stock for product: " + productId);
    }

    public InsufficientStockException(
            final Long productId,
            final Throwable cause) {
        super("Insufficient stock for product: " + productId, cause);
    }
}