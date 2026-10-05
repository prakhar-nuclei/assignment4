package com.nuclei.orderservice.mapper;

import com.nuclei.orderservice.dtos.OrderItemResponseDto;
import com.nuclei.orderservice.dtos.OrderResponseDto;
import com.nuclei.orderservice.entity.Order;
import com.nuclei.orderservice.entity.OrderItem;
import java.util.List;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@NoArgsConstructor
public class OrderMapper {

    public OrderResponseDto toResponseDto(final Order order) {
        final List<OrderItemResponseDto> items = order.getItems()
                .stream()
                .map(this::toItemResponseDto)
                .toList();

        return new OrderResponseDto(
                order.getId(),
                order.getUserId(),
                items,
                order.getTotalAmount(),
                order.getOrderStatusEnum()
        );
    }

    private OrderItemResponseDto toItemResponseDto(final OrderItem orderItem) {
        return new OrderItemResponseDto(
                orderItem.getProductId(),
                orderItem.getProductName(),
                orderItem.getUnitPrice(),
                orderItem.getQuantity(),
                orderItem.getSubtotal()
        );
    }
}