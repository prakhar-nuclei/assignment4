package com.nuclei.productcatalogservice.exception;

public class JwtKeyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public JwtKeyException(String message, Throwable cause) {
        super(message, cause);
    }
}