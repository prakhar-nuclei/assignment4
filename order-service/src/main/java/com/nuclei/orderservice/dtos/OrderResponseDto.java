package com.nuclei.orderservice.dtos;

import com.nuclei.orderservice.enums.OrderStatusEnum;
import java.math.BigDecimal;
import java.util.List;

public record OrderResponseDto(
        Long orderId,
        Long userId,
        List<OrderItemResponseDto> items,
        BigDecimal totalAmount,
        OrderStatusEnum status) {
}