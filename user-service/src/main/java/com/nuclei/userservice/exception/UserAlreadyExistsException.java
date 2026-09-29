package com.nuclei.userservice.exception;

public class UserAlreadyExistsException extends RuntimeException {


    private static final long serialVersionUID = 1L;

    public UserAlreadyExistsException(final String message) {
        super(message);
    }

    public UserAlreadyExistsException(
            final String message,
            final Throwable cause) {
        super(message, cause);
    }
}
