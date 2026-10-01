package com.nuclei.productcatalogservice.exception;

public class InvalidProductRequestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidProductRequestException(final String message) {
        super(message);
    }

    public InvalidProductRequestException(
            final String message,
            final Throwable cause) {
        super(message, cause);
    }
}