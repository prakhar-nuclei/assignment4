package com.nuclei.productcatalogservice.exception;

public class ProductConcurrencyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ProductConcurrencyException(Long productId) {
        super("Unable to acquire lock for product id: " + productId);
    }
}