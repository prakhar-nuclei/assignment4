package com.nuclei.productcatalogservice.service;

import com.nuclei.productcatalogservice.dto.ProductResponseDto;
import java.math.BigDecimal;

public interface ProductService {

    ProductResponseDto getProduct(Long productId);

    ProductResponseDto updateStock(Long productId, Integer quantity);

    ProductResponseDto createProduct(
            String name,
            BigDecimal price,
            Integer stock);

    ProductResponseDto updateProduct(
            Long productId,
            String name,
            BigDecimal price,
            Integer stock);

    void deleteProduct(Long productId);
}
