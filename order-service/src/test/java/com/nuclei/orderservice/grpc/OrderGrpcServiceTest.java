package com.nuclei.orderservice.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import com.nuclei.order.proto.CreateOrderRequest;
import com.nuclei.order.proto.GetOrderRequest;
import com.nuclei.order.proto.OrderResponse;
import com.nuclei.orderservice.dtos.CreateOrderRequestDto;
import com.nuclei.orderservice.dtos.GetOrderRequestDto;
import com.nuclei.orderservice.dtos.OrderResponseDto;
import com.nuclei.orderservice.enums.OrderStatusEnum;
import com.nuclei.orderservice.exception.*;
import com.nuclei.orderservice.grpc.interceptor.AuthenticatedUserInterceptor;
import com.nuclei.orderservice.grpc.mapper.OrderGrpcMapper;
import com.nuclei.orderservice.grpc.server.OrderGrpcService;
import com.nuclei.orderservice.service.OrderService;
import java.math.BigDecimal;
import java.util.List;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
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
        MockitoAnnotations.openMocks(this);
        orderGrpcService =
                new OrderGrpcService(orderService, orderGrpcMapper);
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
                        List.of()
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

        when(orderService.createOrder(userId, requestDto))
                .thenReturn(responseDto);

        when(orderGrpcMapper.toOrderResponse(responseDto))
                .thenReturn(grpcResponse);

        final Context context =
                Context.current().withValue(
                        AuthenticatedUserInterceptor.USER_ID_CONTEXT_KEY,
                        userId
                );

        context.run(() ->
                orderGrpcService.createOrder(
                        request,
                        responseObserver
                )
        );

        verify(orderGrpcMapper).toCreateOrderRequestDto(request);

        verify(orderService).createOrder(
                userId,
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
                        List.of()
                );

        final InvalidOrderRequestException exception =
                new InvalidOrderRequestException(
                        "Invalid create order request"
                );

        when(orderGrpcMapper.toCreateOrderRequestDto(request))
                .thenReturn(requestDto);

        when(orderService.createOrder(userId, requestDto))
                .thenThrow(exception);

        final Context context =
                Context.current().withValue(
                        AuthenticatedUserInterceptor.USER_ID_CONTEXT_KEY,
                        userId
                );

        context.run(() ->
                orderGrpcService.createOrder(
                        request,
                        responseObserver
                )
        );

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
                userId,
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
                        List.of()
                );

        final ProductNotFoundException exception =
                new ProductNotFoundException(100L);

        when(orderGrpcMapper.toCreateOrderRequestDto(request))
                .thenReturn(requestDto);

        when(orderService.createOrder(userId, requestDto))
                .thenThrow(exception);

        final Context context =
                Context.current().withValue(
                        AuthenticatedUserInterceptor.USER_ID_CONTEXT_KEY,
                        userId
                );

        context.run(() ->
                orderGrpcService.createOrder(
                        request,
                        responseObserver
                )
        );

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
                userId,
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
                new CreateOrderRequestDto("order-123", List.of());

        final UserNotFoundException exception =
                new UserNotFoundException(userId);

        when(orderGrpcMapper.toCreateOrderRequestDto(request))
                .thenReturn(requestDto);

        when(orderService.createOrder(userId, requestDto))
                .thenThrow(exception);

        final Context context = Context.current().withValue(
                AuthenticatedUserInterceptor.USER_ID_CONTEXT_KEY,
                userId);

        context.run(() -> orderGrpcService.createOrder(
                request, responseObserver));

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
                new CreateOrderRequestDto("order-123", List.of());

        final InsufficientStockException exception =
                new InsufficientStockException(100L);

        when(orderGrpcMapper.toCreateOrderRequestDto(request))
                .thenReturn(requestDto);

        when(orderService.createOrder(userId, requestDto))
                .thenThrow(exception);

        final Context context = Context.current().withValue(
                AuthenticatedUserInterceptor.USER_ID_CONTEXT_KEY,
                userId);

        context.run(() -> orderGrpcService.createOrder(
                request, responseObserver));

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
                new CreateOrderRequestDto("order-123", List.of());

        final ProductConcurrencyException exception =
                new ProductConcurrencyException(100L);

        when(orderGrpcMapper.toCreateOrderRequestDto(request))
                .thenReturn(requestDto);

        when(orderService.createOrder(userId, requestDto))
                .thenThrow(exception);

        final Context context = Context.current().withValue(
                AuthenticatedUserInterceptor.USER_ID_CONTEXT_KEY,
                userId);

        context.run(() -> orderGrpcService.createOrder(
                request, responseObserver));

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
                new CreateOrderRequestDto("order-123", List.of());

        when(orderGrpcMapper.toCreateOrderRequestDto(request))
                .thenReturn(requestDto);

        when(orderService.createOrder(userId, requestDto))
                .thenThrow(new RuntimeException("Database unavailable"));

        final Context context = Context.current().withValue(
                AuthenticatedUserInterceptor.USER_ID_CONTEXT_KEY,
                userId);

        context.run(() -> orderGrpcService.createOrder(
                request, responseObserver));

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
                        .build();

        final GetOrderRequestDto requestDto =
                new GetOrderRequestDto(orderId);

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

        final Context context = Context.current().withValue(
                AuthenticatedUserInterceptor.USER_ID_CONTEXT_KEY,
                userId);

        context.run(() -> orderGrpcService.getOrder(
                request, responseObserver));

        verify(orderGrpcMapper).toGetOrderRequestDto(request);
        verify(orderService).getOrder(userId, requestDto);
        verify(orderGrpcMapper).toOrderResponse(responseDto);
        verify(responseObserver).onNext(grpcResponse);
        verify(responseObserver).onCompleted();
    }

    @Test
    void getOrderShouldReturnInvalidArgumentWhenRequestIsInvalid() {
        final Long userId = 1L;

        final GetOrderRequest request =
                GetOrderRequest.newBuilder()
                        .setOrderId(0L)
                        .build();

        final GetOrderRequestDto requestDto =
                new GetOrderRequestDto(0L);

        when(orderGrpcMapper.toGetOrderRequestDto(request))
                .thenReturn(requestDto);

        when(orderService.getOrder(userId, requestDto))
                .thenThrow(new InvalidOrderRequestException(
                        "Invalid get order request"));

        final Context context = Context.current().withValue(
                AuthenticatedUserInterceptor.USER_ID_CONTEXT_KEY,
                userId);

        context.run(() -> orderGrpcService.getOrder(
                request, responseObserver));

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
                        .build();

        final GetOrderRequestDto requestDto =
                new GetOrderRequestDto(orderId);

        when(orderGrpcMapper.toGetOrderRequestDto(request))
                .thenReturn(requestDto);

        when(orderService.getOrder(userId, requestDto))
                .thenThrow(new OrderNotFoundException(orderId));

        final Context context = Context.current().withValue(
                AuthenticatedUserInterceptor.USER_ID_CONTEXT_KEY,
                userId);

        context.run(() -> orderGrpcService.getOrder(
                request, responseObserver));

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
                        .build();

        final GetOrderRequestDto requestDto =
                new GetOrderRequestDto(orderId);

        when(orderGrpcMapper.toGetOrderRequestDto(request))
                .thenReturn(requestDto);

        when(orderService.getOrder(userId, requestDto))
                .thenThrow(new RuntimeException("Database unavailable"));

        final Context context = Context.current().withValue(
                AuthenticatedUserInterceptor.USER_ID_CONTEXT_KEY,
                userId);

        context.run(() -> orderGrpcService.getOrder(
                request, responseObserver));

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
    void createOrderShouldReturnInvalidArgumentWhenAuthenticatedUserIsMissing() {
        final CreateOrderRequest request =
                CreateOrderRequest.newBuilder()
                        .setIdempotencyKey("order-123")
                        .build();

        orderGrpcService.createOrder(request, responseObserver);

        final ArgumentCaptor<StatusRuntimeException> exceptionCaptor =
                ArgumentCaptor.forClass(StatusRuntimeException.class);

        verify(responseObserver).onError(exceptionCaptor.capture());

        assertEquals(
                Status.Code.INVALID_ARGUMENT,
                exceptionCaptor.getValue().getStatus().getCode());

        verifyNoInteractions(orderService);
        verifyNoInteractions(orderGrpcMapper);
    }

    @Test
    void getOrderShouldReturnInvalidArgumentWhenAuthenticatedUserIsMissing() {
        final GetOrderRequest request =
                GetOrderRequest.newBuilder()
                        .setOrderId(10L)
                        .build();

        orderGrpcService.getOrder(request, responseObserver);

        final ArgumentCaptor<StatusRuntimeException> exceptionCaptor =
                ArgumentCaptor.forClass(StatusRuntimeException.class);

        verify(responseObserver).onError(exceptionCaptor.capture());

        assertEquals(
                Status.Code.INVALID_ARGUMENT,
                exceptionCaptor.getValue().getStatus().getCode());

        verifyNoInteractions(orderService);
        verifyNoInteractions(orderGrpcMapper);
    }
}
