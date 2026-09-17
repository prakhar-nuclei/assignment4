package com.nuclei.userservice.exception;

public class JwtKeyException extends RuntimeException {

    public JwtKeyException(String message, Throwable cause) {
        super(message, cause);
    }
}
