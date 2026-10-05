package com.nuclei.orderservice.dtos;

import java.math.BigDecimal;

public record OrderItemResponseDto(
        Long productId,
        String productName,
        BigDecimal unitPrice,
        Integer quantity,
        BigDecimal subtotal) {
}