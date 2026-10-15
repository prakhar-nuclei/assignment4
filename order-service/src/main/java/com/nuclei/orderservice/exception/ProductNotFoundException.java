package com.nuclei.orderservice.exception;

public class ProductNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ProductNotFoundException(final Long productId) {
        super("Product not found: " + productId);
    }

    public ProductNotFoundException(
            final Long productId,
            final Throwable cause) {
        super("Product not found: " + productId, cause);
    }
}