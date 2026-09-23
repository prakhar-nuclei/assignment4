package com.nuclei.userservice.exception;

public class InvalidUserRequestException extends RuntimeException {


    private static final long serialVersionUID = 1L;

    public InvalidUserRequestException(final String message) {
        super(message);
    }
}
