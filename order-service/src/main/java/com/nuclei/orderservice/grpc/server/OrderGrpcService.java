package com.nuclei.orderservice.grpc.server;

import com.nuclei.order.proto.CreateOrderRequest;
import com.nuclei.order.proto.GetOrderRequest;
import com.nuclei.order.proto.OrderResponse;
import com.nuclei.order.proto.OrderServiceGrpc;
import com.nuclei.orderservice.dtos.CreateOrderRequestDto;
import com.nuclei.orderservice.dtos.GetOrderRequestDto;
import com.nuclei.orderservice.dtos.OrderResponseDto;
import com.nuclei.orderservice.exception.InsufficientStockException;
import com.nuclei.orderservice.exception.InvalidOrderRequestException;
import com.nuclei.orderservice.exception.OrderNotFoundException;
import com.nuclei.orderservice.exception.ProductConcurrencyException;
import com.nuclei.orderservice.exception.ProductNotFoundException;
import com.nuclei.orderservice.exception.UserNotFoundException;
import com.nuclei.orderservice.grpc.interceptor.AuthenticatedUserInterceptor;
import com.nuclei.orderservice.grpc.mapper.OrderGrpcMapper;
import com.nuclei.orderservice.service.OrderService;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
public class OrderGrpcService extends OrderServiceGrpc.OrderServiceImplBase {

    private final OrderService orderService;
    private final OrderGrpcMapper orderGrpcMapper;

    public OrderGrpcService(
            final OrderService orderService,
            final OrderGrpcMapper orderGrpcMapper) {
        super();
        this.orderService = orderService;
        this.orderGrpcMapper = orderGrpcMapper;
    }

    @Override
    @SuppressWarnings({
            "PMD.AvoidCatchingGenericException"
    })
    public void createOrder(
            final CreateOrderRequest request,
            final StreamObserver<OrderResponse> responseObserver) {

        try {
            final Long userId = getAuthenticatedUserId();

            final CreateOrderRequestDto requestDto =
                    orderGrpcMapper.toCreateOrderRequestDto(request);

            final OrderResponseDto responseDto =
                    orderService.createOrder(userId, requestDto);

            responseObserver.onNext(
                    orderGrpcMapper.toOrderResponse(responseDto)
            );
            responseObserver.onCompleted();
        } catch (final InvalidOrderRequestException
                       | UserNotFoundException
                       | ProductNotFoundException
                       | OrderNotFoundException
                       | InsufficientStockException
                       | ProductConcurrencyException exception) {
            responseObserver.onError(toStatusException(exception));
        } catch (final Exception exception) {

            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription("Unexpected error while processing order request.")
                            .withCause(exception)
                            .asRuntimeException()
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
            final Long userId = getAuthenticatedUserId();

            final GetOrderRequestDto requestDto =
                    orderGrpcMapper.toGetOrderRequestDto(request);

            final OrderResponseDto responseDto =
                    orderService.getOrder(userId, requestDto);

            responseObserver.onNext(
                    orderGrpcMapper.toOrderResponse(responseDto)
            );
            responseObserver.onCompleted();
        } catch (final InvalidOrderRequestException
                       | OrderNotFoundException exception) {
            responseObserver.onError(toStatusException(exception));
        } catch (final Exception exception) {

            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription("Unexpected error while processing order request.")
                            .withCause(exception)
                            .asRuntimeException()
            );
        }
    }

    private Long getAuthenticatedUserId() {
        final Long userId =
                AuthenticatedUserInterceptor.USER_ID_CONTEXT_KEY.get();

        if (userId == null || userId <= 0) {
            throw new InvalidOrderRequestException(
                    "Authenticated user id is missing or invalid."
            );
        }

        return userId;
    }

    private StatusRuntimeException toStatusException(
            final Exception exception) {

        final Status status;

        if (exception instanceof InvalidOrderRequestException) {
            status = Status.INVALID_ARGUMENT;
        } else if (exception instanceof UserNotFoundException
                || exception instanceof ProductNotFoundException
                || exception instanceof OrderNotFoundException) {
            status = Status.NOT_FOUND;
        } else if (exception instanceof InsufficientStockException) {
            status = Status.FAILED_PRECONDITION;
        } else if (exception instanceof ProductConcurrencyException) {
            status = Status.ABORTED;
        } else {
            status = Status.INTERNAL;
        }

        return status
                .withDescription(exception.getMessage())
                .withCause(exception)
                .asRuntimeException();
    }
}