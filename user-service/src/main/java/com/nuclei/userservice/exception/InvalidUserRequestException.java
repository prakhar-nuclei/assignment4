package com.nuclei.userservice.exception;

public class InvalidUserRequestException extends RuntimeException {

    public InvalidUserRequestException(final String message) {
        super(message);
    }
}
