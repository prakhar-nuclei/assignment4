package com.nuclei.orderservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public final class OrderServiceApplication {

    private OrderServiceApplication() {
    }

    public static void main(final String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}