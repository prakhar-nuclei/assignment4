package com.nuclei.orderservice.dtos;

import java.math.BigDecimal;

public record ProductResponseDto(
        Long productId,
        String name,
        BigDecimal price,
        Integer stock) {
}