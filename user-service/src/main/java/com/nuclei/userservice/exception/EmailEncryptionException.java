package com.nuclei.userservice.exception;

public class EmailEncryptionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public EmailEncryptionException(final String message) {
        super(message);
    }

    public EmailEncryptionException(final String message, final Throwable cause) {
        super(message, cause);
    }
}