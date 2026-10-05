package com.nuclei.orderservice.exception;

public class OrderNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public OrderNotFoundException(final Long orderId) {
        super("Order not found with id: " + orderId);
    }
}