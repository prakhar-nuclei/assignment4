package com.nuclei.productcatalogservice.service;

import com.nuclei.productcatalogservice.dto.ProductResponseDto;
import java.math.BigDecimal;

public interface ProductService {

    ProductResponseDto getProduct(Long productId);

    ProductResponseDto updateStock(Long productId, Integer quantity,String operationId);

    ProductResponseDto createProduct(
            String name,
            BigDecimal price,
            Integer stock);

    ProductResponseDto updateProduct(
            Long productId,
            String name,
            BigDecimal price,
            Integer stock);

    ProductResponseDto compensateStock(
            Long productId,
            Integer quantity,
            String compensationId);

    void deleteProduct(Long productId);
}
