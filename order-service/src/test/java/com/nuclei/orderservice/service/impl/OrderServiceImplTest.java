package com.nuclei.orderservice.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.nuclei.orderservice.cache.OrderIdempotencyCache;
import com.nuclei.orderservice.dtos.*;
import com.nuclei.orderservice.entity.Order;
import com.nuclei.orderservice.entity.OrderItem;
import com.nuclei.orderservice.enums.OrderStatusEnum;
import com.nuclei.orderservice.exception.*;
import com.nuclei.orderservice.grpc.client.ProductGrpcClient;
import com.nuclei.orderservice.mapper.OrderMapper;
import com.nuclei.orderservice.repo.OrderRepository;
import com.nuclei.orderservice.validator.CreateOrderRequestValidator;
import com.nuclei.orderservice.validator.GetOrderRequestValidator;
import com.nuclei.product.proto.StockOperationDirection;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;


@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private ProductGrpcClient productGrpcClient;

    @Mock
    private CreateOrderRequestValidator createOrderRequestValidator;

    @Mock
    private GetOrderRequestValidator getOrderRequestValidator;

    @Mock
    private OrderIdempotencyCache orderIdempotencyCache;

    @InjectMocks
    private OrderServiceImpl orderService;


    @BeforeEach
    void setUp() {
        lenient().when(
                orderIdempotencyCache.get(anyLong(), anyString())
        ).thenReturn(Optional.empty());

        lenient().when(
                orderMapper.toOrder(
                        anyLong(),
                        anyString(),
                        any(BigDecimal.class))
        ).thenAnswer(invocation -> {
            final Order order = new Order();

            order.setUserId(invocation.getArgument(0));
            order.setIdempotencyKey(invocation.getArgument(1));
            order.setTotalAmount(invocation.getArgument(2));
            order.setOrderStatusEnum(OrderStatusEnum.CONFIRMED);

            return order;
        });

        lenient().when(
                orderMapper.toOrderItem(
                        any(ProductResponseDto.class),
                        any(OrderItemRequestDto.class),
                        any(BigDecimal.class),
                        any(BigDecimal.class))
        ).thenAnswer(invocation -> {
            final ProductResponseDto product = invocation.getArgument(0);
            final OrderItemRequestDto item = invocation.getArgument(1);
            final BigDecimal unitPrice = invocation.getArgument(2);
            final BigDecimal subtotal = invocation.getArgument(3);

            final OrderItem orderItem = new OrderItem();
            orderItem.setProductId(product.productId());
            orderItem.setProductName(product.name());
            orderItem.setUnitPrice(unitPrice);
            orderItem.setQuantity(item.quantity());
            orderItem.setSubtotal(subtotal);

            return orderItem;
        });
    }

    @Test
    void createOrderShouldReturnExistingOrderForSameIdempotencyKey() {
        final Long userId = 1L;
        final String idempotencyKey = "order-123";

        final CreateOrderRequestDto request =
                new CreateOrderRequestDto(
                        idempotencyKey,
                        java.util.List.of(),
                        new UserDetailsDto(
                                userId,
                                "Prakhar",
                                "prakhar@example.com"
                        )
                );

        final Order existingOrder = new Order();

        final OrderResponseDto expectedResponse =
                new OrderResponseDto(
                        10L,
                        userId,
                        java.util.List.of(),
                        null,
                        null
                );

        when(orderRepository.findByUserIdAndIdempotencyKey(
                userId,
                idempotencyKey
        )).thenReturn(Optional.of(existingOrder));

        when(orderMapper.toResponseDto(existingOrder))
                .thenReturn(expectedResponse);

        final OrderResponseDto actualResponse =
                orderService.createOrder(request);

        assertSame(expectedResponse, actualResponse);

        verify(createOrderRequestValidator).validate(request);

        verify(orderRepository).findByUserIdAndIdempotencyKey(
                userId,
                idempotencyKey
        );

        verify(orderMapper).toResponseDto(existingOrder);

        verify(productGrpcClient, never()).getProduct(anyLong());

        verify(productGrpcClient, never())
                .updateStock(anyLong(), anyInt(), anyString());

        verify(orderIdempotencyCache).put(
                userId,
                idempotencyKey,
                expectedResponse
        );
    }

    @Test
    void createOrderShouldPersistOrderWhenOrderDoesNotExist() {
        final Long userId = 1L;
        final String idempotencyKey = "order-123";

        final CreateOrderRequestDto request =
                new CreateOrderRequestDto(
                        idempotencyKey,
                        java.util.List.of(),
                        new UserDetailsDto(
                                userId,
                                "Prakhar",
                                "prakhar@example.com"
                        )
                );

        final Order order = new Order();

        final OrderResponseDto expectedResponse =
                new OrderResponseDto(
                        10L,
                        userId,
                        java.util.List.of(),
                        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                        OrderStatusEnum.CONFIRMED
                );

        when(orderRepository.findByUserIdAndIdempotencyKey(
                userId,
                idempotencyKey
        )).thenReturn(Optional.empty());

        when(orderRepository.save(any(Order.class)))
                .thenReturn(order);

        when(orderMapper.toResponseDto(order))
                .thenReturn(expectedResponse);

        final OrderResponseDto actualResponse =
                orderService.createOrder(request);

        assertSame(expectedResponse, actualResponse);

        verify(createOrderRequestValidator).validate(request);

        verify(orderRepository).findByUserIdAndIdempotencyKey(
                userId,
                idempotencyKey
        );

        verify(orderRepository).save(any(Order.class));

        verify(orderMapper).toResponseDto(order);

        verifyNoInteractions(productGrpcClient);
    }

    @Test
    void createOrderShouldReturnCachedResponseWithoutDatabaseLookup() {
        final Long userId = 1L;
        final String idempotencyKey = "order-123";

        final CreateOrderRequestDto request =
                new CreateOrderRequestDto(
                        idempotencyKey,
                        java.util.List.of(),
                        new UserDetailsDto(
                                userId,
                                "Prakhar",
                                "prakhar@example.com"
                        )
                );

        final OrderResponseDto cachedResponse =
                new OrderResponseDto(
                        10L,
                        userId,
                        java.util.List.of(),
                        new BigDecimal("200.00"),
                        OrderStatusEnum.CONFIRMED
                );

        when(orderIdempotencyCache.get(userId, idempotencyKey))
                .thenReturn(Optional.of(cachedResponse));

        final OrderResponseDto actualResponse =
                orderService.createOrder(request);

        assertSame(cachedResponse, actualResponse);

        verify(createOrderRequestValidator).validate(request);

        verify(orderIdempotencyCache).get(userId, idempotencyKey);

        verify(orderRepository, never())
                .findByUserIdAndIdempotencyKey(anyLong(), anyString());

        verify(orderRepository, never()).save(any(Order.class));

        verifyNoInteractions(productGrpcClient);

        verify(orderIdempotencyCache, never())
                .put(anyLong(), anyString(), any(OrderResponseDto.class));
    }

    @Test
    void createOrderShouldFetchProductAndUpdateStock() {
        final Long userId = 1L;
        final String idempotencyKey = "order-123";
        final Long productId = 100L;
        final Integer quantity = 2;

        final CreateOrderRequestDto request =
                new CreateOrderRequestDto(
                        idempotencyKey,
                        java.util.List.of(
                                new OrderItemRequestDto(productId, quantity)
                        ),
                        new UserDetailsDto(
                                userId,
                                "Prakhar",
                                "prakhar@example.com"
                        )
                );

        final ProductResponseDto productResponse =
                new ProductResponseDto(
                        productId,
                        "Laptop",
                        new BigDecimal("100.00"),
                        10
                );

        final Order savedOrder = new Order();

        final OrderResponseDto expectedResponse =
                new OrderResponseDto(
                        10L,
                        userId,
                        java.util.List.of(),
                        new BigDecimal("200.00"),
                        OrderStatusEnum.CONFIRMED
                );

        when(orderRepository.findByUserIdAndIdempotencyKey(
                userId,
                idempotencyKey
        )).thenReturn(Optional.empty());

        when(productGrpcClient.getProduct(productId))
                .thenReturn(productResponse);

        when(productGrpcClient.updateStock(
                eq(productId),
                eq(quantity),
                anyString()
        )).thenReturn(productResponse);

        when(orderRepository.save(any(Order.class)))
                .thenReturn(savedOrder);

        when(orderMapper.toResponseDto(savedOrder))
                .thenReturn(expectedResponse);

        final OrderResponseDto actualResponse =
                orderService.createOrder(request);

        assertSame(expectedResponse, actualResponse);

        verify(createOrderRequestValidator).validate(request);

        verify(productGrpcClient).getProduct(productId);

        verify(productGrpcClient).updateStock(
                eq(productId),
                eq(quantity),
                anyString()
        );

        verify(orderRepository).save(any(Order.class));

        verify(orderMapper).toResponseDto(savedOrder);

        verify(orderIdempotencyCache).put(
                userId,
                idempotencyKey,
                expectedResponse
        );
    }

    @Test
    void createOrderShouldThrowWhenProductDoesNotExist() {
        final Long userId = 1L;
        final String idempotencyKey = "order-123";
        final Long productId = 100L;
        final Integer quantity = 2;

        final CreateOrderRequestDto request =
                new CreateOrderRequestDto(
                        idempotencyKey,
                        java.util.List.of(
                                new OrderItemRequestDto(productId, quantity)
                        ),
                        new UserDetailsDto(
                                userId,
                                "Prakhar",
                                "prakhar@example.com"
                        )
                );

        final ProductNotFoundException exception =
                new ProductNotFoundException(productId);

        when(orderRepository.findByUserIdAndIdempotencyKey(
                userId,
                idempotencyKey
        )).thenReturn(Optional.empty());

        when(productGrpcClient.getProduct(productId))
                .thenThrow(exception);

        assertThrows(
                ProductNotFoundException.class,
                () -> orderService.createOrder(request)
        );

        verify(createOrderRequestValidator).validate(request);

        verify(productGrpcClient).getProduct(productId);

        verify(productGrpcClient, never())
                .updateStock(anyLong(), anyInt(), anyString());

        verify(orderRepository, never())
                .save(any(Order.class));

        verify(orderMapper, never())
                .toResponseDto(any(Order.class));
    }

    @Test
    void createOrderShouldThrowWhenStockIsInsufficient() {
        final Long userId = 1L;
        final String idempotencyKey = "order-123";
        final Long productId = 100L;
        final Integer quantity = 5;

        final CreateOrderRequestDto request =
                new CreateOrderRequestDto(
                        idempotencyKey,
                        java.util.List.of(
                                new OrderItemRequestDto(productId, quantity)
                        ),
                        new UserDetailsDto(
                                userId,
                                "Prakhar",
                                "prakhar@example.com"
                        )
                );

        final ProductResponseDto productResponse =
                new ProductResponseDto(
                        productId,
                        "Laptop",
                        new BigDecimal("100.00"),
                        2
                );

        final InsufficientStockException exception =
                new InsufficientStockException(productId);

        when(orderRepository.findByUserIdAndIdempotencyKey(
                userId,
                idempotencyKey
        )).thenReturn(Optional.empty());

        when(productGrpcClient.getProduct(productId))
                .thenReturn(productResponse);

        when(productGrpcClient.updateStock(
                eq(productId),
                eq(quantity),
                anyString()
        )).thenThrow(exception);

        assertThrows(
                InsufficientStockException.class,
                () -> orderService.createOrder(request)
        );

        verify(createOrderRequestValidator).validate(request);

        verify(productGrpcClient).getProduct(productId);

        verify(productGrpcClient).updateStock(
                eq(productId),
                eq(quantity),
                anyString()
        );

        verify(productGrpcClient, never())
                .compensateStock(
                        anyLong(),
                        anyInt(),
                        anyString(),
                        any(StockOperationDirection.class)
                );

        verify(orderRepository, never())
                .save(any(Order.class));

        verify(orderMapper, never())
                .toResponseDto(any(Order.class));
    }

    @Test
    void createOrderShouldCompensateSuccessfulStockWhenLaterProductFails() {
        final Long userId = 1L;
        final String idempotencyKey = "order-123";

        final Long firstProductId = 100L;
        final Long secondProductId = 200L;

        final Integer firstQuantity = 2;
        final Integer secondQuantity = 5;

        final CreateOrderRequestDto request =
                new CreateOrderRequestDto(
                        idempotencyKey,
                        java.util.List.of(
                                new OrderItemRequestDto(
                                        firstProductId,
                                        firstQuantity
                                ),
                                new OrderItemRequestDto(
                                        secondProductId,
                                        secondQuantity
                                )
                        ),
                        new UserDetailsDto(
                                userId,
                                "Prakhar",
                                "prakhar@example.com"
                        )
                );

        final ProductResponseDto firstProduct =
                new ProductResponseDto(
                        firstProductId,
                        "Laptop",
                        new BigDecimal("100.00"),
                        10
                );

        final ProductResponseDto secondProduct =
                new ProductResponseDto(
                        secondProductId,
                        "Mouse",
                        new BigDecimal("50.00"),
                        2
                );

        final ProductConcurrencyException exception =
                new ProductConcurrencyException(secondProductId);

        when(orderRepository.findByUserIdAndIdempotencyKey(
                userId,
                idempotencyKey
        )).thenReturn(Optional.empty());

        when(productGrpcClient.getProduct(firstProductId))
                .thenReturn(firstProduct);

        when(productGrpcClient.getProduct(secondProductId))
                .thenReturn(secondProduct);

        when(productGrpcClient.updateStock(
                eq(firstProductId),
                eq(firstQuantity),
                anyString()
        )).thenReturn(firstProduct);

        when(productGrpcClient.updateStock(
                eq(secondProductId),
                eq(secondQuantity),
                anyString()
        )).thenThrow(exception);

        assertThrows(
                ProductConcurrencyException.class,
                () -> orderService.createOrder(request)
        );

        verify(createOrderRequestValidator).validate(request);

        verify(productGrpcClient).getProduct(firstProductId);
        verify(productGrpcClient).getProduct(secondProductId);

        verify(productGrpcClient).updateStock(
                eq(firstProductId),
                eq(firstQuantity),
                anyString()
        );

        verify(productGrpcClient).updateStock(
                eq(secondProductId),
                eq(secondQuantity),
                anyString()
        );

        verify(productGrpcClient).compensateStock(
                eq(firstProductId),
                eq(firstQuantity),
                anyString(),
                eq(StockOperationDirection.INCREASE)
        );

        verify(productGrpcClient, never()).compensateStock(
                eq(secondProductId),
                anyInt(),
                anyString(),
                any(StockOperationDirection.class)
        );

        verify(orderRepository, never()).save(any(Order.class));

        verify(orderMapper, never()).toResponseDto(any(Order.class));
    }

    @Test
    void createOrderShouldCompensateStockWhenOrderSaveFails() {
        final Long userId = 1L;
        final String idempotencyKey = "order-123";
        final Long productId = 100L;
        final Integer quantity = 2;

        final CreateOrderRequestDto request =
                new CreateOrderRequestDto(
                        idempotencyKey,
                        java.util.List.of(
                                new OrderItemRequestDto(
                                        productId,
                                        quantity
                                )
                        ),
                        new UserDetailsDto(
                                userId,
                                "Prakhar",
                                "prakhar@example.com"
                        )
                );

        final ProductResponseDto productResponse =
                new ProductResponseDto(
                        productId,
                        "Laptop",
                        new BigDecimal("100.00"),
                        10
                );

        final DataIntegrityViolationException exception =
                new DataIntegrityViolationException(
                        "Order could not be saved"
                );

        when(orderRepository.findByUserIdAndIdempotencyKey(
                userId,
                idempotencyKey
        )).thenReturn(Optional.empty());

        when(productGrpcClient.getProduct(productId))
                .thenReturn(productResponse);

        when(productGrpcClient.updateStock(
                eq(productId),
                eq(quantity),
                anyString()
        )).thenReturn(productResponse);

        when(orderRepository.save(any(Order.class)))
                .thenThrow(exception);

        when(orderRepository.findByUserIdAndIdempotencyKey(
                userId,
                idempotencyKey
        )).thenReturn(
                Optional.empty(),
                Optional.empty()
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> orderService.createOrder(request)
        );

        verify(createOrderRequestValidator).validate(request);

        verify(productGrpcClient).getProduct(productId);

        verify(productGrpcClient).updateStock(
                eq(productId),
                eq(quantity),
                anyString()
        );

        verify(productGrpcClient).compensateStock(
                eq(productId),
                eq(quantity),
                anyString(),
                eq(StockOperationDirection.INCREASE)
        );

        verify(orderRepository).save(any(Order.class));

        verify(orderMapper, never()).toResponseDto(any(Order.class));
    }

    @Test
    void createOrderShouldReturnConcurrentOrderAfterDataIntegrityViolation() {
        final Long userId = 1L;
        final String idempotencyKey = "order-123";

        final CreateOrderRequestDto request =
                new CreateOrderRequestDto(
                        idempotencyKey,
                        java.util.List.of(),
                        new UserDetailsDto(
                                userId,
                                "Prakhar",
                                "prakhar@example.com"
                        )
                );

        final Order concurrentOrder = new Order();

        final OrderResponseDto expectedResponse =
                new OrderResponseDto(
                        20L,
                        userId,
                        java.util.List.of(),
                        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                        OrderStatusEnum.CONFIRMED
                );

        final DataIntegrityViolationException exception =
                new DataIntegrityViolationException(
                        "Duplicate idempotency key"
                );

        when(orderRepository.findByUserIdAndIdempotencyKey(
                userId,
                idempotencyKey
        )).thenReturn(
                Optional.empty(),
                Optional.of(concurrentOrder)
        );

        when(orderRepository.save(any(Order.class)))
                .thenThrow(exception);

        when(orderMapper.toResponseDto(concurrentOrder))
                .thenReturn(expectedResponse);

        final OrderResponseDto actualResponse =
                orderService.createOrder(request);

        assertSame(expectedResponse, actualResponse);

        verify(createOrderRequestValidator).validate(request);

        verify(orderRepository, times(2))
                .findByUserIdAndIdempotencyKey(
                        userId,
                        idempotencyKey
                );

        verify(orderRepository).save(any(Order.class));

        verify(orderMapper).toResponseDto(concurrentOrder);

        verifyNoInteractions(productGrpcClient);
    }

    @Test
    void createOrderShouldCalculateItemSubtotalsAndTotalAmount() {
        final Long userId = 1L;
        final String idempotencyKey = "order-123";

        final Long firstProductId = 100L;
        final Long secondProductId = 200L;

        final CreateOrderRequestDto request =
                new CreateOrderRequestDto(
                        idempotencyKey,
                        java.util.List.of(
                                new OrderItemRequestDto(firstProductId, 2),
                                new OrderItemRequestDto(secondProductId, 3)
                        ),
                        new UserDetailsDto(
                                userId,
                                "Prakhar",
                                "prakhar@example.com"
                        )
                );

        final ProductResponseDto firstProduct =
                new ProductResponseDto(
                        firstProductId,
                        "Laptop",
                        new BigDecimal("100.125"),
                        10
                );

        final ProductResponseDto secondProduct =
                new ProductResponseDto(
                        secondProductId,
                        "Mouse",
                        new BigDecimal("50.555"),
                        10
                );

        final Order savedOrder = new Order();

        final OrderResponseDto expectedResponse =
                new OrderResponseDto(
                        10L,
                        userId,
                        java.util.List.of(),
                        new BigDecimal("351.94"),
                        OrderStatusEnum.CONFIRMED
                );

        when(orderRepository.findByUserIdAndIdempotencyKey(
                userId,
                idempotencyKey
        )).thenReturn(Optional.empty());

        when(productGrpcClient.getProduct(firstProductId))
                .thenReturn(firstProduct);

        when(productGrpcClient.getProduct(secondProductId))
                .thenReturn(secondProduct);

        when(productGrpcClient.updateStock(
                eq(firstProductId),
                eq(2),
                anyString()
        )).thenReturn(firstProduct);

        when(productGrpcClient.updateStock(
                eq(secondProductId),
                eq(3),
                anyString()
        )).thenReturn(secondProduct);

        when(orderRepository.save(any(Order.class)))
                .thenReturn(savedOrder);

        when(orderMapper.toResponseDto(savedOrder))
                .thenReturn(expectedResponse);

        final OrderResponseDto actualResponse =
                orderService.createOrder(request);

        assertSame(expectedResponse, actualResponse);

        final ArgumentCaptor<Order> orderCaptor =
                ArgumentCaptor.forClass(Order.class);

        verify(orderRepository).save(orderCaptor.capture());

        final Order savedOrderArgument = orderCaptor.getValue();

        assertEquals(
                userId,
                savedOrderArgument.getUserId()
        );

        assertEquals(
                idempotencyKey,
                savedOrderArgument.getIdempotencyKey()
        );

        assertEquals(
                OrderStatusEnum.CONFIRMED,
                savedOrderArgument.getOrderStatusEnum()
        );

        assertEquals(
                new BigDecimal("351.94"),
                savedOrderArgument.getTotalAmount()
        );

        assertEquals(2, savedOrderArgument.getItems().size());

        final OrderItem firstItem =
                savedOrderArgument.getItems().get(0);

        assertEquals(
                new BigDecimal("100.13"),
                firstItem.getUnitPrice()
        );

        assertEquals(
                new BigDecimal("200.26"),
                firstItem.getSubtotal()
        );

        final OrderItem secondItem =
                savedOrderArgument.getItems().get(1);

        assertEquals(
                new BigDecimal("50.56"),
                secondItem.getUnitPrice()
        );

        assertEquals(
                new BigDecimal("151.68"),
                secondItem.getSubtotal()
        );

        assertEquals(
                savedOrderArgument,
                firstItem.getOrder()
        );

        assertEquals(
                savedOrderArgument,
                secondItem.getOrder()
        );
    }

    @Test
    void getOrderShouldReturnExistingOrderForUser() {
        final Long userId = 1L;
        final Long orderId = 10L;

        final GetOrderRequestDto request =
                new GetOrderRequestDto(orderId, userId);

        final Order order = new Order();

        final OrderResponseDto expectedResponse =
                new OrderResponseDto(
                        orderId,
                        userId,
                        java.util.List.of(),
                        new BigDecimal("200.00"),
                        OrderStatusEnum.CONFIRMED
                );

        when(orderRepository.findByIdAndUserId(
                orderId,
                userId
        )).thenReturn(Optional.of(order));

        when(orderMapper.toResponseDto(order))
                .thenReturn(expectedResponse);

        final OrderResponseDto actualResponse =
                orderService.getOrder(userId, request);

        assertSame(expectedResponse, actualResponse);

        verify(getOrderRequestValidator).validate(request);

        verify(orderRepository).findByIdAndUserId(
                orderId,
                userId
        );

        verify(orderMapper).toResponseDto(order);
    }

    @Test
    void getOrderShouldThrowWhenOrderDoesNotExist() {
        final Long userId = 1L;
        final Long orderId = 10L;

        final GetOrderRequestDto request =
                new GetOrderRequestDto(orderId, userId);

        when(orderRepository.findByIdAndUserId(
                orderId,
                userId
        )).thenReturn(Optional.empty());

        assertThrows(
                OrderNotFoundException.class,
                () -> orderService.getOrder(userId, request)
        );

        verify(getOrderRequestValidator).validate(request);

        verify(orderRepository).findByIdAndUserId(
                orderId,
                userId
        );

        verify(orderMapper, never())
                .toResponseDto(any(Order.class));
    }

    @Test
    void createOrderShouldStopWhenRequestValidationFails() {
        final Long userId = 1L;

        final CreateOrderRequestDto request =
                new CreateOrderRequestDto(
                        "",
                        java.util.List.of(),
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

        doThrow(exception)
                .when(createOrderRequestValidator)
                .validate(request);

        assertThrows(
                InvalidOrderRequestException.class,
                () -> orderService.createOrder(request)
        );

        verify(createOrderRequestValidator).validate(request);

        verifyNoInteractions(orderRepository);
        verifyNoInteractions(productGrpcClient);
        verifyNoInteractions(orderMapper);
    }

    @Test
    void getOrderShouldStopWhenRequestValidationFails() {
        final Long userId = 1L;

        final GetOrderRequestDto request =
                new GetOrderRequestDto(0L,userId);

        final InvalidOrderRequestException exception =
                new InvalidOrderRequestException(
                        "Invalid get order request"
                );

        doThrow(exception)
                .when(getOrderRequestValidator)
                .validate(request);

        assertThrows(
                InvalidOrderRequestException.class,
                () -> orderService.getOrder(userId, request)
        );

        verify(getOrderRequestValidator).validate(request);

        verifyNoInteractions(orderRepository);
        verifyNoInteractions(orderMapper);
    }

    @Test
    void createOrderShouldPreserveOriginalExceptionWhenCompensationFails() {
        final Long userId = 1L;
        final String idempotencyKey = "order-123";
        final Long productId = 100L;
        final Integer quantity = 2;

        final CreateOrderRequestDto request =
                new CreateOrderRequestDto(
                        idempotencyKey,
                        java.util.List.of(
                                new OrderItemRequestDto(
                                        productId,
                                        quantity
                                )
                        ),
                        new UserDetailsDto(
                                userId,
                                "Prakhar",
                                "prakhar@example.com"
                        )
                );

        final ProductResponseDto productResponse =
                new ProductResponseDto(
                        productId,
                        "Laptop",
                        new BigDecimal("100.00"),
                        10
                );

        final DataIntegrityViolationException originalException =
                new DataIntegrityViolationException(
                        "Order save failed"
                );

        final RuntimeException compensationException =
                new RuntimeException(
                        "Compensation failed"
                );

        when(orderRepository.findByUserIdAndIdempotencyKey(
                userId,
                idempotencyKey
        )).thenReturn(
                Optional.empty(),
                Optional.empty()
        );

        when(productGrpcClient.getProduct(productId))
                .thenReturn(productResponse);

        when(productGrpcClient.updateStock(
                eq(productId),
                eq(quantity),
                anyString()
        )).thenReturn(productResponse);

        when(orderRepository.save(any(Order.class)))
                .thenThrow(originalException);

        when(productGrpcClient.compensateStock(
                eq(productId),
                eq(quantity),
                anyString(),
                eq(StockOperationDirection.INCREASE)
        )).thenThrow(compensationException);

        final DataIntegrityViolationException thrownException =
                assertThrows(
                        DataIntegrityViolationException.class,
                        () -> orderService.createOrder(request)
                );

        assertSame(originalException, thrownException);

        assertEquals(
                1,
                thrownException.getSuppressed().length
        );

        assertSame(
                compensationException,
                thrownException.getSuppressed()[0]
        );

        verify(productGrpcClient).compensateStock(
                eq(productId),
                eq(quantity),
                anyString(),
                eq(StockOperationDirection.INCREASE)
        );

        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void createOrderShouldGenerateDeterministicOperationId() {
        final Long userId = 1L;
        final String idempotencyKey = "order-123";
        final Long productId = 100L;
        final Integer quantity = 2;

        final CreateOrderRequestDto request =
                new CreateOrderRequestDto(
                        idempotencyKey,
                        java.util.List.of(
                                new OrderItemRequestDto(productId, quantity)
                        ),
                        new UserDetailsDto(
                                userId,
                                "Prakhar",
                                "prakhar@example.com"
                        )
                );

        final ProductResponseDto productResponse =
                new ProductResponseDto(
                        productId,
                        "Laptop",
                        new BigDecimal("100.00"),
                        10
                );

        final Order savedOrder = new Order();

        final OrderResponseDto expectedResponse =
                new OrderResponseDto(
                        10L,
                        userId,
                        java.util.List.of(),
                        new BigDecimal("200.00"),
                        OrderStatusEnum.CONFIRMED
                );

        when(orderRepository.findByUserIdAndIdempotencyKey(
                userId,
                idempotencyKey
        )).thenReturn(Optional.empty());

        when(productGrpcClient.getProduct(productId))
                .thenReturn(productResponse);

        when(productGrpcClient.updateStock(
                eq(productId),
                eq(quantity),
                anyString()
        )).thenReturn(productResponse);

        when(orderRepository.save(any(Order.class)))
                .thenReturn(savedOrder);

        when(orderMapper.toResponseDto(savedOrder))
                .thenReturn(expectedResponse);

        orderService.createOrder(request);

        final ArgumentCaptor<String> operationIdCaptor =
                ArgumentCaptor.forClass(String.class);

        verify(productGrpcClient).updateStock(
                eq(productId),
                eq(quantity),
                operationIdCaptor.capture()
        );

        final String expectedOperationId =
                UUID.nameUUIDFromBytes(
                        (userId + ":" + idempotencyKey + ":0")
                                .getBytes(StandardCharsets.UTF_8)
                ).toString();

        assertEquals(
                expectedOperationId,
                operationIdCaptor.getValue()
        );
    }

    @Test
    void createOrderShouldGenerateDeterministicCompensationId() {
        final Long userId = 1L;
        final String idempotencyKey = "order-123";
        final Long productId = 100L;
        final Integer quantity = 2;

        final CreateOrderRequestDto request =
                new CreateOrderRequestDto(
                        idempotencyKey,
                        java.util.List.of(
                                new OrderItemRequestDto(productId, quantity)
                        ),
                        new UserDetailsDto(
                                userId,
                                "Prakhar",
                                "prakhar@example.com"
                        )
                );

        final ProductResponseDto productResponse =
                new ProductResponseDto(
                        productId,
                        "Laptop",
                        new BigDecimal("100.00"),
                        10
                );

        final DataIntegrityViolationException exception =
                new DataIntegrityViolationException(
                        "Order save failed"
                );

        when(orderRepository.findByUserIdAndIdempotencyKey(
                userId,
                idempotencyKey
        )).thenReturn(
                Optional.empty(),
                Optional.empty()
        );

        when(productGrpcClient.getProduct(productId))
                .thenReturn(productResponse);

        when(productGrpcClient.updateStock(
                eq(productId),
                eq(quantity),
                anyString()
        )).thenReturn(productResponse);

        when(orderRepository.save(any(Order.class)))
                .thenThrow(exception);

        final ArgumentCaptor<String> compensationIdCaptor =
                ArgumentCaptor.forClass(String.class);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> orderService.createOrder(request)
        );

        verify(productGrpcClient).compensateStock(
                eq(productId),
                eq(quantity),
                compensationIdCaptor.capture(),
                eq(StockOperationDirection.INCREASE)
        );

        final String operationId =
                UUID.nameUUIDFromBytes(
                        (userId + ":" + idempotencyKey + ":0")
                                .getBytes(StandardCharsets.UTF_8)
                ).toString();

        final String expectedCompensationId =
                UUID.nameUUIDFromBytes(
                        (operationId + ":compensation")
                                .getBytes(StandardCharsets.UTF_8)
                ).toString();

        assertEquals(
                expectedCompensationId,
                compensationIdCaptor.getValue()
        );
    }

}