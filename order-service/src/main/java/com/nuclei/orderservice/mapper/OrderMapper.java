package com.nuclei.orderservice.mapper;

import com.nuclei.orderservice.dtos.OrderItemRequestDto;
import com.nuclei.orderservice.dtos.OrderItemResponseDto;
import com.nuclei.orderservice.dtos.OrderResponseDto;
import com.nuclei.orderservice.dtos.ProductResponseDto;
import com.nuclei.orderservice.entity.Order;
import com.nuclei.orderservice.entity.OrderItem;
import java.math.BigDecimal;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(target = "orderId", source = "id")
    @Mapping(target = "status", source = "orderStatusEnum")
    OrderResponseDto toResponseDto(Order order);

    OrderItemResponseDto toItemResponseDto(OrderItem orderItem);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "order", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "productId", source = "product.productId")
    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "unitPrice", source = "unitPrice")
    @Mapping(target = "quantity", source = "item.quantity")
    @Mapping(target = "subtotal", source = "subtotal")
    OrderItem toOrderItem(
            ProductResponseDto product,
            OrderItemRequestDto item,
            BigDecimal unitPrice,
            BigDecimal subtotal);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "items", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "idempotencyKey", source = "idempotencyKey")
    @Mapping(target = "totalAmount", source = "totalAmount")
    @Mapping(target = "orderStatusEnum", constant = "CONFIRMED")
    Order toOrder(
            Long userId,
            String idempotencyKey,
            BigDecimal totalAmount);
}