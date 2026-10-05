package com.nuclei.orderservice.dtos;

import java.util.List;

public record CreateOrderRequestDto(
        String idempotencyKey,
        List<OrderItemRequestDto> items) {
}