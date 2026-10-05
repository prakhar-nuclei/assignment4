package com.nuclei.orderservice.grpc.mapper;

import com.nuclei.order.proto.CreateOrderRequest;
import com.nuclei.order.proto.GetOrderRequest;
import com.nuclei.order.proto.OrderItemResponse;
import com.nuclei.order.proto.OrderResponse;
import com.nuclei.orderservice.dtos.CreateOrderRequestDto;
import com.nuclei.orderservice.dtos.GetOrderRequestDto;
import com.nuclei.orderservice.dtos.OrderItemRequestDto;
import com.nuclei.orderservice.dtos.OrderResponseDto;
import java.util.List;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@NoArgsConstructor
public class OrderGrpcMapper {

    public CreateOrderRequestDto toCreateOrderRequestDto(
            final CreateOrderRequest request) {

        final List<OrderItemRequestDto> items = request.getItemsList()
                .stream()
                .map(item -> new OrderItemRequestDto(
                        item.getProductId(),
                        item.getQuantity()
                ))
                .toList();

        return new CreateOrderRequestDto(
                request.getIdempotencyKey(),
                items
        );
    }

    public GetOrderRequestDto toGetOrderRequestDto(
            final GetOrderRequest request) {

        return new GetOrderRequestDto(
                request.getOrderId()
        );
    }

    public OrderResponse toOrderResponse(
            final OrderResponseDto responseDto) {

        final List<OrderItemResponse> items = responseDto.items()
                .stream()
                .map(item -> OrderItemResponse.newBuilder()
                        .setProductId(item.productId())
                        .setProductName(item.productName())
                        .setUnitPrice(item.unitPrice().toPlainString())
                        .setQuantity(item.quantity())
                        .setSubtotal(item.subtotal().toPlainString())
                        .build()
                )
                .toList();

        return OrderResponse.newBuilder()
                .setOrderId(responseDto.orderId())
                .setUserId(responseDto.userId())
                .addAllItems(items)
                .setTotalAmount(responseDto.totalAmount().toPlainString())
                .setStatus(responseDto.status().name())
                .build();
    }
}