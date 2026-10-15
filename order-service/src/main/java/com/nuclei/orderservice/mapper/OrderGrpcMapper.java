package com.nuclei.orderservice.mapper;

import com.nuclei.order.proto.CreateOrderRequest;
import com.nuclei.order.proto.GetOrderRequest;
import com.nuclei.order.proto.OrderItemRequest;
import com.nuclei.order.proto.OrderItemResponse;
import com.nuclei.order.proto.OrderResponse;
import com.nuclei.orderservice.dtos.CreateOrderRequestDto;
import com.nuclei.orderservice.dtos.GetOrderRequestDto;
import com.nuclei.orderservice.dtos.OrderItemRequestDto;
import com.nuclei.orderservice.dtos.OrderItemResponseDto;
import com.nuclei.orderservice.dtos.OrderResponseDto;
import java.util.List;
import lombok.NoArgsConstructor;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
@NoArgsConstructor
public abstract class OrderGrpcMapper {

    @Mapping(target = "items", source = "itemsList")
    @Mapping(
            target = "user",
            expression = "java(request.hasUser()"
                    + " ? new UserDetailsDto("
                    + "request.getUser().getUserId(),"
                    + "request.getUser().getName(),"
                    + "request.getUser().getEmail())"
                    + " : null)")
    public abstract CreateOrderRequestDto toCreateOrderRequestDto(
            CreateOrderRequest request);

    public abstract OrderItemRequestDto toOrderItemRequestDto(
            OrderItemRequest request);

    public abstract GetOrderRequestDto toGetOrderRequestDto(
            GetOrderRequest request);

    public OrderResponse toOrderResponse(
           final OrderResponseDto responseDto) {

       final List<OrderItemResponse> items = responseDto.items()
                .stream()
                .map(this::toOrderItemResponse)
                .toList();

        return OrderResponse.newBuilder()
                .setOrderId(responseDto.orderId())
                .setUserId(responseDto.userId())
                .addAllItems(items)
                .setTotalAmount(responseDto.totalAmount().toPlainString())
                .setStatus(responseDto.status().name())
                .build();
    }

    protected OrderItemResponse toOrderItemResponse(
           final OrderItemResponseDto item) {

        return OrderItemResponse.newBuilder()
                .setProductId(item.productId())
                .setProductName(item.productName())
                .setUnitPrice(item.unitPrice().toPlainString())
                .setQuantity(item.quantity())
                .setSubtotal(item.subtotal().toPlainString())
                .build();
    }
}