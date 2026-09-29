package com.nuclei.userservice.exception;

public class IdempotencyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public IdempotencyException(final String message) {
        super(message);
    }

    public IdempotencyException(
            final String message,
            final Throwable cause) {
        super(message, cause);
    }
}