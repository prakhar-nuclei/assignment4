package com.nuclei.orderservice.grpc.server;

import com.nuclei.order.proto.CreateOrderRequest;
import com.nuclei.order.proto.GetOrderRequest;
import com.nuclei.order.proto.OrderResponse;
import com.nuclei.order.proto.OrderServiceGrpc;
import com.nuclei.orderservice.dtos.CreateOrderRequestDto;
import com.nuclei.orderservice.dtos.GetOrderRequestDto;
import com.nuclei.orderservice.dtos.OrderResponseDto;
import com.nuclei.orderservice.grpc.exception.OrderGrpcExceptionMapper;
import com.nuclei.orderservice.mapper.OrderGrpcMapper;
import com.nuclei.orderservice.service.OrderService;
import io.grpc.stub.StreamObserver;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
public class OrderGrpcService extends OrderServiceGrpc.OrderServiceImplBase {

    private final OrderService orderService;
    private final OrderGrpcMapper orderGrpcMapper;
    private final OrderGrpcExceptionMapper orderGrpcExceptionMapper;

    public OrderGrpcService(
            final OrderService orderService,
            final OrderGrpcMapper orderGrpcMapper,
            final OrderGrpcExceptionMapper orderGrpcExceptionMapper) {
        super();
        this.orderService = orderService;
        this.orderGrpcMapper = orderGrpcMapper;
        this.orderGrpcExceptionMapper = orderGrpcExceptionMapper;
    }

    @Override
    @SuppressWarnings({
            "PMD.AvoidCatchingGenericException"
    })
    public void createOrder(
            final CreateOrderRequest request,
            final StreamObserver<OrderResponse> responseObserver) {

        try {
            final CreateOrderRequestDto requestDto =
                    orderGrpcMapper.toCreateOrderRequestDto(request);

            final OrderResponseDto responseDto =
                    orderService.createOrder(requestDto);

            responseObserver.onNext(
                    orderGrpcMapper.toOrderResponse(responseDto)
            );
            responseObserver.onCompleted();
        } catch (final Exception exception) {
            responseObserver.onError(
                    orderGrpcExceptionMapper.toStatusException(exception)
            );
        }
    }

    @Override
    @SuppressWarnings({
            "PMD.AvoidCatchingGenericException"
    })
    public void getOrder(
            final GetOrderRequest request,
            final StreamObserver<OrderResponse> responseObserver) {

        try {
            final GetOrderRequestDto requestDto =
                    orderGrpcMapper.toGetOrderRequestDto(request);

            final OrderResponseDto responseDto =
                    orderService.getOrder(requestDto.userId(), requestDto);

            responseObserver.onNext(
                    orderGrpcMapper.toOrderResponse(responseDto)
            );
            responseObserver.onCompleted();
        } catch (final Exception exception) {
            responseObserver.onError(
                    orderGrpcExceptionMapper.toStatusException(exception)
            );
        }
    }
}