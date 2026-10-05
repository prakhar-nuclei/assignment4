package com.nuclei.orderservice.dtos;

public record OrderItemRequestDto(
        Long productId,
        Integer quantity) {
}