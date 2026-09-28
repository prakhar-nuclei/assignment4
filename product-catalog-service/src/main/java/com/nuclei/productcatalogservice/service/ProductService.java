package com.nuclei.productcatalogservice.service;

import com.nuclei.productcatalogservice.dto.ProductResponseDto;

public interface ProductService {

    ProductResponseDto getProduct(Long productId);

    ProductResponseDto updateStock(Long productId, Integer quantity);
}
