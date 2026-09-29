package com.nuclei.userservice.exception;

public class RedisLockAcquisitionException extends RuntimeException {


    private static final long serialVersionUID = 1L;

    public RedisLockAcquisitionException(final String message) {
        super(message);
    }

    public RedisLockAcquisitionException(
            final String message,
            final Throwable cause) {
        super(message, cause);
    }
}