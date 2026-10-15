package com.nuclei.productcatalogservice.dto;

import java.math.BigDecimal;

public record ProductResponseDto(
        Long productId,
        String name,
        BigDecimal price,
        Integer stock
) {
}