package com.nuclei.productcatalogservice.mapper;

import com.nuclei.productcatalogservice.dto.ProductResponseDto;
import com.nuclei.productcatalogservice.entity.Product;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    public ProductResponseDto toResponseDto(Product product) {
        return new ProductResponseDto(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getStock()
        );
    }
}