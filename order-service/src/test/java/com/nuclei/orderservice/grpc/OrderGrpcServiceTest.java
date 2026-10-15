package com.nuclei.orderservice.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.nuclei.order.proto.CreateOrderRequest;
import com.nuclei.order.proto.GetOrderRequest;
import com.nuclei.order.proto.OrderResponse;
import com.nuclei.orderservice.dtos.CreateOrderRequestDto;
import com.nuclei.orderservice.dtos.GetOrderRequestDto;
import com.nuclei.orderservice.dtos.OrderResponseDto;
import com.nuclei.orderservice.dtos.UserDetailsDto;
import com.nuclei.orderservice.enums.OrderStatusEnum;
import com.nuclei.orderservice.exception.InsufficientStockException;
import com.nuclei.orderservice.exception.InvalidOrderRequestException;
import com.nuclei.orderservice.exception.OrderNotFoundException;
import com.nuclei.orderservice.exception.ProductConcurrencyException;
import com.nuclei.orderservice.exception.ProductNotFoundException;
import com.nuclei.orderservice.exception.UserNotFoundException;
import com.nuclei.orderservice.grpc.exception.OrderGrpcExceptionMapper;
import com.nuclei.orderservice.grpc.server.OrderGrpcService;
import com.nuclei.orderservice.mapper.OrderGrpcMapper;
import com.nuclei.orderservice.service.OrderService;
import java.math.BigDecimal;
import java.util.List;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class OrderGrpcServiceTest {

    @Mock
    private OrderService orderService;

    @Mock
    private OrderGrpcMapper orderGrpcMapper;

    @Mock
    private StreamObserver<OrderResponse> responseObserver;

    private OrderGrpcService orderGrpcService;

    @BeforeEach
    void setUp() {
        final OrderGrpcExceptionMapper exceptionMapper =
                new OrderGrpcExceptionMapper();

        orderGrpcService = new OrderGrpcService(
                orderService,
                orderGrpcMapper,
                exceptionMapper
        );
    }

    @Test
    void createOrderShouldReturnSuccessfulResponse() {
        final Long userId = 1L;

        final CreateOrderRequest request =
                CreateOrderRequest.newBuilder()
                        .setIdempotencyKey("order-123")
                        .build();

        final CreateOrderRequestDto requestDto =
                new CreateOrderRequestDto(
                        "order-123",
                        List.of(),
                        new UserDetailsDto(
                                userId,
                                "Prakhar",
                                "prakhar@example.com"
                        )
                );

        final OrderResponseDto responseDto =
                new OrderResponseDto(
                        10L,
                        userId,
                        List.of(),
                        null,
                        null
                );

        final OrderResponse grpcResponse =
                OrderResponse.newBuilder()
                        .setOrderId(10L)
                        .setUserId(userId)
                        .build();

        when(orderGrpcMapper.toCreateOrderRequestDto(request))
                .thenReturn(requestDto);

        when(orderService.createOrder(requestDto))
                .thenReturn(responseDto);

        when(orderGrpcMapper.toOrderResponse(responseDto))
                .thenReturn(grpcResponse);

        orderGrpcService.createOrder(request, responseObserver);

        verify(orderGrpcMapper).toCreateOrderRequestDto(request);

        verify(orderService).createOrder(
                requestDto
        );

        verify(orderGrpcMapper).toOrderResponse(responseDto);

        verify(responseObserver).onNext(grpcResponse);

        verify(responseObserver).onCompleted();
    }

    @Test
    void createOrderShouldReturnInvalidArgumentWhenRequestIsInvalid() {
        final Long userId = 1L;

        final CreateOrderRequest request =
                CreateOrderRequest.newBuilder()
                        .setIdempotencyKey("order-123")
                        .build();

        final CreateOrderRequestDto requestDto =
                new CreateOrderRequestDto(
                        "order-123",
                        List.of(),
                        new UserDetailsDto(
                                userId,
                                "Prakhar",
                                "prakhar@example.com"
                        )
                );

        final InvalidOrderRequestException exception =
                new InvalidOrderRequestException(
                        "Invalid create order request"
                );

        when(orderGrpcMapper.toCreateOrderRequestDto(request))
                .thenReturn(requestDto);

        when(orderService.createOrder(requestDto))
                .thenThrow(exception);

        orderGrpcService.createOrder(request, responseObserver);

        final ArgumentCaptor<StatusRuntimeException> exceptionCaptor =
                ArgumentCaptor.forClass(StatusRuntimeException.class);

        verify(responseObserver).onError(exceptionCaptor.capture());

        final StatusRuntimeException grpcException =
                exceptionCaptor.getValue();

        assertEquals(
                Status.Code.INVALID_ARGUMENT,
                grpcException.getStatus().getCode()
        );

        assertEquals(
                "Invalid create order request",
                grpcException.getStatus().getDescription()
        );

        verify(orderGrpcMapper).toCreateOrderRequestDto(request);

        verify(orderService).createOrder(
                requestDto
        );
    }

    @Test
    void createOrderShouldReturnNotFoundWhenProductDoesNotExist() {
        final Long userId = 1L;

        final CreateOrderRequest request =
                CreateOrderRequest.newBuilder()
                        .setIdempotencyKey("order-123")
                        .build();

        final CreateOrderRequestDto requestDto =
                new CreateOrderRequestDto(
                        "order-123",
                        List.of(),
                        new UserDetailsDto(
                                userId,
                                "Prakhar",
                                "prakhar@example.com"
                        )
                );

        final ProductNotFoundException exception =
                new ProductNotFoundException(100L);

        when(orderGrpcMapper.toCreateOrderRequestDto(request))
                .thenReturn(requestDto);

        when(orderService.createOrder(requestDto))
                .thenThrow(exception);

        orderGrpcService.createOrder(request, responseObserver);

        final ArgumentCaptor<StatusRuntimeException> exceptionCaptor =
                ArgumentCaptor.forClass(StatusRuntimeException.class);

        verify(responseObserver).onError(exceptionCaptor.capture());

        final StatusRuntimeException grpcException =
                exceptionCaptor.getValue();

        assertEquals(
                Status.Code.NOT_FOUND,
                grpcException.getStatus().getCode()
        );

        verify(orderGrpcMapper).toCreateOrderRequestDto(request);

        verify(orderService).createOrder(
                requestDto
        );

        verify(responseObserver, never()).onNext(any(OrderResponse.class));
        verify(responseObserver, never()).onCompleted();
    }

    @Test
    void createOrderShouldReturnNotFoundWhenUserDoesNotExist() {
        final Long userId = 1L;

        final CreateOrderRequest request =
                CreateOrderRequest.newBuilder()
                        .setIdempotencyKey("order-123")
                        .build();

        final CreateOrderRequestDto requestDto =
                new CreateOrderRequestDto("order-123",
                        List.of(),
                        new UserDetailsDto(
                                userId,
                                "Prakhar",
                                "prakhar@example.com"
                        ));

        final UserNotFoundException exception =
                new UserNotFoundException(userId);

        when(orderGrpcMapper.toCreateOrderRequestDto(request))
                .thenReturn(requestDto);

        when(orderService.createOrder(requestDto))
                .thenThrow(exception);

        orderGrpcService.createOrder(request, responseObserver);

        final ArgumentCaptor<StatusRuntimeException> exceptionCaptor =
                ArgumentCaptor.forClass(StatusRuntimeException.class);

        verify(responseObserver).onError(exceptionCaptor.capture());

        assertEquals(
                Status.Code.NOT_FOUND,
                exceptionCaptor.getValue().getStatus().getCode());

        verify(responseObserver, never()).onNext(any(OrderResponse.class));
        verify(responseObserver, never()).onCompleted();
    }

    @Test
    void createOrderShouldReturnFailedPreconditionWhenStockIsInsufficient() {
        final Long userId = 1L;

        final CreateOrderRequest request =
                CreateOrderRequest.newBuilder()
                        .setIdempotencyKey("order-123")
                        .build();

        final CreateOrderRequestDto requestDto =
                new CreateOrderRequestDto("order-123",
                        List.of(),
                        new UserDetailsDto(
                                userId,
                                "Prakhar",
                                "prakhar@example.com"
                        ));

        final InsufficientStockException exception =
                new InsufficientStockException(100L);

        when(orderGrpcMapper.toCreateOrderRequestDto(request))
                .thenReturn(requestDto);

        when(orderService.createOrder(requestDto))
                .thenThrow(exception);

        orderGrpcService.createOrder(request, responseObserver);

        final ArgumentCaptor<StatusRuntimeException> exceptionCaptor =
                ArgumentCaptor.forClass(StatusRuntimeException.class);

        verify(responseObserver).onError(exceptionCaptor.capture());

        assertEquals(
                Status.Code.FAILED_PRECONDITION,
                exceptionCaptor.getValue().getStatus().getCode());

        verify(responseObserver, never()).onNext(any(OrderResponse.class));
        verify(responseObserver, never()).onCompleted();
    }

    @Test
    void createOrderShouldReturnAbortedWhenProductConcurrencyOccurs() {
        final Long userId = 1L;

        final CreateOrderRequest request =
                CreateOrderRequest.newBuilder()
                        .setIdempotencyKey("order-123")
                        .build();

        final CreateOrderRequestDto requestDto =
                new CreateOrderRequestDto("order-123", List.of(),
                        new UserDetailsDto(
                                userId,
                                "Prakhar",
                                "prakhar@example.com"
                        ));

        final ProductConcurrencyException exception =
                new ProductConcurrencyException(100L);

        when(orderGrpcMapper.toCreateOrderRequestDto(request))
                .thenReturn(requestDto);

        when(orderService.createOrder(requestDto))
                .thenThrow(exception);

        orderGrpcService.createOrder(request, responseObserver);

        final ArgumentCaptor<StatusRuntimeException> exceptionCaptor =
                ArgumentCaptor.forClass(StatusRuntimeException.class);

        verify(responseObserver).onError(exceptionCaptor.capture());

        assertEquals(
                Status.Code.ABORTED,
                exceptionCaptor.getValue().getStatus().getCode());

        verify(responseObserver, never()).onNext(any(OrderResponse.class));
        verify(responseObserver, never()).onCompleted();
    }

    @Test
    void createOrderShouldReturnInternalWhenUnexpectedExceptionOccurs() {
        final Long userId = 1L;

        final CreateOrderRequest request =
                CreateOrderRequest.newBuilder()
                        .setIdempotencyKey("order-123")
                        .build();

        final CreateOrderRequestDto requestDto =
                new CreateOrderRequestDto("order-123", List.of(),
                        new UserDetailsDto(
                                userId,
                                "Prakhar",
                                "prakhar@example.com"
                        ));

        when(orderGrpcMapper.toCreateOrderRequestDto(request))
                .thenReturn(requestDto);

        when(orderService.createOrder(requestDto))
                .thenThrow(new RuntimeException("Database unavailable"));

        orderGrpcService.createOrder(request, responseObserver);

        final ArgumentCaptor<StatusRuntimeException> exceptionCaptor =
                ArgumentCaptor.forClass(StatusRuntimeException.class);

        verify(responseObserver).onError(exceptionCaptor.capture());

        final StatusRuntimeException grpcException =
                exceptionCaptor.getValue();

        assertEquals(
                Status.Code.INTERNAL,
                grpcException.getStatus().getCode());

        assertEquals(
                "Unexpected error while processing order request.",
                grpcException.getStatus().getDescription());

        verify(responseObserver, never()).onNext(any(OrderResponse.class));
        verify(responseObserver, never()).onCompleted();
    }

    @Test
    void getOrderShouldReturnSuccessfulResponse() {
        final Long userId = 1L;
        final Long orderId = 10L;

        final GetOrderRequest request =
                GetOrderRequest.newBuilder()
                        .setOrderId(orderId)
                        .setUserId(userId)
                        .build();

        final GetOrderRequestDto requestDto =
                new GetOrderRequestDto(orderId, userId);

        final OrderResponseDto responseDto =
                new OrderResponseDto(
                        orderId,
                        userId,
                        List.of(),
                        BigDecimal.TEN,
                        OrderStatusEnum.CONFIRMED);

        final OrderResponse grpcResponse =
                OrderResponse.newBuilder()
                        .setOrderId(orderId)
                        .setUserId(userId)
                        .setTotalAmount("10.00")
                        .setStatus("CONFIRMED")
                        .build();

        when(orderGrpcMapper.toGetOrderRequestDto(request))
                .thenReturn(requestDto);

        when(orderService.getOrder(userId, requestDto))
                .thenReturn(responseDto);

        when(orderGrpcMapper.toOrderResponse(responseDto))
                .thenReturn(grpcResponse);

        orderGrpcService.getOrder(request, responseObserver);

        verify(orderGrpcMapper).toGetOrderRequestDto(request);
        verify(orderService).getOrder(userId, requestDto);
        verify(orderGrpcMapper).toOrderResponse(responseDto);
        verify(responseObserver).onNext(grpcResponse);
        verify(responseObserver).onCompleted();
    }

    @Test
    void getOrderShouldReturnInvalidArgumentWhenRequestIsInvalid() {
        final Long userId = 1L;
        final Long orderId = 0L;


        final GetOrderRequest request =
                GetOrderRequest.newBuilder()
                        .setOrderId(orderId)
                        .setUserId(userId)
                        .build();

        final GetOrderRequestDto requestDto =
                new GetOrderRequestDto(orderId, userId);

        when(orderGrpcMapper.toGetOrderRequestDto(request))
                .thenReturn(requestDto);

        when(orderService.getOrder(userId, requestDto))
                .thenThrow(new InvalidOrderRequestException(
                        "Invalid get order request"));


        orderGrpcService.getOrder(request, responseObserver);

        final ArgumentCaptor<StatusRuntimeException> exceptionCaptor =
                ArgumentCaptor.forClass(StatusRuntimeException.class);

        verify(responseObserver).onError(exceptionCaptor.capture());

        assertEquals(
                Status.Code.INVALID_ARGUMENT,
                exceptionCaptor.getValue().getStatus().getCode());

        verify(responseObserver, never()).onNext(any(OrderResponse.class));
        verify(responseObserver, never()).onCompleted();
    }

    @Test
    void getOrderShouldReturnNotFoundWhenOrderDoesNotExist() {
        final Long userId = 1L;
        final Long orderId = 10L;

        final GetOrderRequest request =
                GetOrderRequest.newBuilder()
                        .setOrderId(orderId)
                        .setUserId(userId)
                        .build();

        final GetOrderRequestDto requestDto =
                new GetOrderRequestDto(orderId, userId);

        when(orderGrpcMapper.toGetOrderRequestDto(request))
                .thenReturn(requestDto);

        when(orderService.getOrder(userId, requestDto))
                .thenThrow(new OrderNotFoundException(orderId));

        orderGrpcService.getOrder(request, responseObserver);

        final ArgumentCaptor<StatusRuntimeException> exceptionCaptor =
                ArgumentCaptor.forClass(StatusRuntimeException.class);

        verify(responseObserver).onError(exceptionCaptor.capture());

        assertEquals(
                Status.Code.NOT_FOUND,
                exceptionCaptor.getValue().getStatus().getCode());

        verify(responseObserver, never()).onNext(any(OrderResponse.class));
        verify(responseObserver, never()).onCompleted();
    }

    @Test
    void getOrderShouldReturnInternalWhenUnexpectedExceptionOccurs() {
        final Long userId = 1L;
        final Long orderId = 10L;

        final GetOrderRequest request =
                GetOrderRequest.newBuilder()
                        .setOrderId(orderId)
                        .setUserId(userId)
                        .build();

        final GetOrderRequestDto requestDto =
                new GetOrderRequestDto(orderId, userId);

        when(orderGrpcMapper.toGetOrderRequestDto(request))
                .thenReturn(requestDto);

        when(orderService.getOrder(userId, requestDto))
                .thenThrow(new RuntimeException("Database unavailable"));

        orderGrpcService.getOrder(request, responseObserver);

        final ArgumentCaptor<StatusRuntimeException> exceptionCaptor =
                ArgumentCaptor.forClass(StatusRuntimeException.class);

        verify(responseObserver).onError(exceptionCaptor.capture());

        final StatusRuntimeException grpcException =
                exceptionCaptor.getValue();

        assertEquals(
                Status.Code.INTERNAL,
                grpcException.getStatus().getCode());

        assertEquals(
                "Unexpected error while processing order request.",
                grpcException.getStatus().getDescription());

        verify(responseObserver, never()).onNext(any(OrderResponse.class));
        verify(responseObserver, never()).onCompleted();
    }

    @Test
    void getOrderShouldReturnInvalidArgumentWhenUserIdIsInvalid() {
        final Long orderId = 10L;
        final Long userId = 0L;

        final GetOrderRequest request =
                GetOrderRequest.newBuilder()
                        .setOrderId(orderId)
                        .setUserId(userId)
                        .build();

        final GetOrderRequestDto requestDto =
                new GetOrderRequestDto(orderId, userId);

        when(orderGrpcMapper.toGetOrderRequestDto(request))
                .thenReturn(requestDto);

        when(orderService.getOrder(userId, requestDto))
                .thenThrow(new InvalidOrderRequestException(
                        "Authenticated user id must be positive."));

        orderGrpcService.getOrder(request, responseObserver);

        final ArgumentCaptor<StatusRuntimeException> exceptionCaptor =
                ArgumentCaptor.forClass(StatusRuntimeException.class);

        verify(responseObserver).onError(exceptionCaptor.capture());

        assertEquals(
                Status.Code.INVALID_ARGUMENT,
                exceptionCaptor.getValue().getStatus().getCode());

        verify(orderService).getOrder(userId, requestDto);
        verify(responseObserver, never()).onNext(any(OrderResponse.class));
        verify(responseObserver, never()).onCompleted();
    }
}
