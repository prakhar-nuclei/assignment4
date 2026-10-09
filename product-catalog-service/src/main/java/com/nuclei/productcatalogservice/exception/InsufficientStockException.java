package com.nuclei.productcatalogservice.exception;

public class InsufficientStockException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InsufficientStockException(final Long productId, final Integer requestedQuantity) {
        super("Insufficient stock for product id: " + productId
                + " for quantity: " + requestedQuantity);
    }

}