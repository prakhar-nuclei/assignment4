package com.nuclei.productcatalogservice.exception;

public class ProductNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ProductNotFoundException(final Long productId) {
        super("Product not found with id: " + productId);
    }
}