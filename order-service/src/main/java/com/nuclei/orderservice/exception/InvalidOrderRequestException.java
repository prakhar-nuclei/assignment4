package com.nuclei.orderservice.exception;

public class InvalidOrderRequestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidOrderRequestException(final String message) {
        super(message);
    }

    public InvalidOrderRequestException(
            final String message,
            final Throwable cause) {
        super(message, cause);
    }
}