package com.nuclei.productcatalogservice.exception;

public class InvalidStockOperationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidStockOperationException() {
        super("Stock quantity cannot be zero");
    }
}