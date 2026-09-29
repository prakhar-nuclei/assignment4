package com.nuclei.productcatalogservice.mapper;

import com.nuclei.product.proto.ProductResponse;
import com.nuclei.productcatalogservice.dto.ProductResponseDto;
import org.springframework.stereotype.Component;

@Component
public class ProductGrpcMapper {

    public ProductResponse toProductResponse(ProductResponseDto product) {
        return ProductResponse.newBuilder()
                .setProductId(product.productId())
                .setName(product.name())
                .setPrice(product.price().toPlainString())
                .setStock(product.stock())
                .build();
    }
}