package com.nuclei.orderservice.exception;

public class UserNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UserNotFoundException(final Long userId) {
        super("User not found: " + userId);
    }

    public UserNotFoundException(
            final Long userId,
            final Throwable cause) {
        super("User not found: " + userId, cause);
    }
}