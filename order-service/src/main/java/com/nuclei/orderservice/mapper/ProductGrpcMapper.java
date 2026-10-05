package com.nuclei.orderservice.mapper;

import com.nuclei.orderservice.dtos.ProductResponseDto;
import com.nuclei.product.proto.ProductResponse;
import java.math.BigDecimal;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@NoArgsConstructor
public class ProductGrpcMapper {

    public ProductResponseDto toProductResponseDto(final ProductResponse response) {
        return new ProductResponseDto(
                response.getProductId(),
                response.getName(),
                new BigDecimal(response.getPrice()),
                response.getStock()
        );
    }
}