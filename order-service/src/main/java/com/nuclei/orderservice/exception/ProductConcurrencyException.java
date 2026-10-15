package com.nuclei.orderservice.exception;

public class ProductConcurrencyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ProductConcurrencyException(final Long productId) {
        super("Product stock concurrency conflict: " + productId);
    }

    public ProductConcurrencyException(
            final Long productId,
            final Throwable cause) {
        super("Product stock concurrency conflict: " + productId, cause);
    }
}