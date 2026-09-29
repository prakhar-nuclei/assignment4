package com.nuclei.userservice.exception;

public class IdempotencyKeyConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public IdempotencyKeyConflictException(final String message) {
        super(message);
    }

    public IdempotencyKeyConflictException(
            final String message,
            final Throwable cause) {
        super(message, cause);
    }
}