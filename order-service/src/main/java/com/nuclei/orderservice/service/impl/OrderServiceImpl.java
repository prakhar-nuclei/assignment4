package com.nuclei.orderservice.service.impl;

import com.nuclei.orderservice.cache.OrderIdempotencyCache;
import com.nuclei.orderservice.dtos.CreateOrderRequestDto;
import com.nuclei.orderservice.dtos.GetOrderRequestDto;
import com.nuclei.orderservice.dtos.OrderItemRequestDto;
import com.nuclei.orderservice.dtos.OrderResponseDto;
import com.nuclei.orderservice.dtos.ProductResponseDto;
import com.nuclei.orderservice.entity.Order;
import com.nuclei.orderservice.entity.OrderItem;
import com.nuclei.orderservice.exception.OrderNotFoundException;
import com.nuclei.orderservice.grpc.client.ProductGrpcClient;
import com.nuclei.orderservice.mapper.OrderMapper;
import com.nuclei.orderservice.repo.OrderRepository;
import com.nuclei.orderservice.service.OrderService;
import com.nuclei.orderservice.util.StockOperationIdUtils;
import com.nuclei.orderservice.validator.CreateOrderRequestValidator;
import com.nuclei.orderservice.validator.GetOrderRequestValidator;
import com.nuclei.product.proto.StockOperationDirection;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;


@Service
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final ProductGrpcClient productGrpcClient;
    private final CreateOrderRequestValidator createOrderRequestValidator;
    private final GetOrderRequestValidator getOrderRequestValidator;
    private final OrderIdempotencyCache orderIdempotencyCache;

    public OrderServiceImpl(
            final OrderRepository orderRepository,
            final OrderMapper orderMapper,
            final ProductGrpcClient productGrpcClient,
            final CreateOrderRequestValidator createOrderRequestValidator,
            final GetOrderRequestValidator getOrderRequestValidator,
            final OrderIdempotencyCache orderIdempotencyCache) {
        this.orderRepository = orderRepository;
        this.orderMapper = orderMapper;
        this.productGrpcClient = productGrpcClient;
        this.createOrderRequestValidator = createOrderRequestValidator;
        this.getOrderRequestValidator = getOrderRequestValidator;
        this.orderIdempotencyCache = orderIdempotencyCache;
    }

    @Override
    public OrderResponseDto createOrder(
            final CreateOrderRequestDto request) {

        createOrderRequestValidator.validate(request);

        final Long userId = request.user().userId();
        final String idempotencyKey = request.idempotencyKey();

        final OrderResponseDto response;
        final Optional<OrderResponseDto> cachedResponse =
                orderIdempotencyCache.get(userId, idempotencyKey);

        if (cachedResponse.isPresent()) {
            response = cachedResponse.get();
        } else {
            final Order existingOrder = orderRepository
                    .findByUserIdAndIdempotencyKey(
                            userId,
                            idempotencyKey)
                    .orElse(null);

            if (existingOrder != null) {
                response = orderMapper.toResponseDto(existingOrder);
            } else {
                response = createNewOrder(userId, request);
            }

            orderIdempotencyCache.put(
                    userId,
                    idempotencyKey,
                    response);
        }

        return response;
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private OrderResponseDto createNewOrder(
            final Long userId,
            final CreateOrderRequestDto request) {

        final List<OrderItem> orderItems = new ArrayList<>();
        final List<StockMutation> successfulStockMutations =
                new ArrayList<>();

        OrderResponseDto response;

        try {
            populateOrderItems(
                    userId,
                    request,
                    orderItems,
                    successfulStockMutations
            );

            final BigDecimal totalAmount = orderItems.stream()
                    .map(OrderItem::getSubtotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);

            final Order order = buildOrder(
                    userId,
                    request.idempotencyKey(),
                    orderItems,
                    totalAmount
            );

            final Order savedOrder = orderRepository.save(order);
            response = orderMapper.toResponseDto(savedOrder);

        } catch (final DataIntegrityViolationException exception) {
            response = handleDataIntegrityViolation(
                    userId,
                    request.idempotencyKey(),
                    successfulStockMutations,
                    exception
            );
        } catch (final RuntimeException exception) {
            compensateStockMutations(
                    successfulStockMutations,
                    exception
            );
            throw exception;
        }

        return response;
    }

    private void populateOrderItems(
            final Long userId,
            final CreateOrderRequestDto request,
            final List<OrderItem> orderItems,
            final List<StockMutation> successfulStockMutations) {

        for (int itemIndex = 0;
             itemIndex < request.items().size();
             itemIndex++) {

            final OrderItemRequestDto item = request.items().get(itemIndex);

            final ProductResponseDto product =
                    productGrpcClient.getProduct(item.productId());

            final String operationId =
                    StockOperationIdUtils.generateOperationId(
                            userId,
                            request.idempotencyKey(),
                            itemIndex);

            final String compensationId =
                    StockOperationIdUtils.generateCompensationId(operationId);

            productGrpcClient.updateStock(
                    item.productId(),
                    item.quantity(),
                    operationId
            );

            successfulStockMutations.add(
                    new StockMutation(
                            item.productId(),
                            item.quantity(),
                            operationId,
                            compensationId,
                            StockOperationDirection.DECREASE
                    )
            );

            orderItems.add(createOrderItem(product, item));
        }
    }

    private OrderItem createOrderItem(
            final ProductResponseDto product,
            final OrderItemRequestDto item) {

        final BigDecimal unitPrice = product.price()
                .setScale(2, RoundingMode.HALF_UP);

        final BigDecimal subtotal = unitPrice
                .multiply(BigDecimal.valueOf(item.quantity()))
                .setScale(2, RoundingMode.HALF_UP);

        return orderMapper.toOrderItem(
                product,
                item,
                unitPrice,
                subtotal);
    }

    private Order buildOrder(
            final Long userId,
            final String idempotencyKey,
            final List<OrderItem> orderItems,
            final BigDecimal totalAmount) {

        final Order order = orderMapper.toOrder(
                userId,
                idempotencyKey,
                totalAmount);

        orderItems.stream()
                .forEach(orderItem -> orderItem.setOrder(order));

        order.setItems(orderItems);

        return order;
    }

    private OrderResponseDto handleDataIntegrityViolation(
            final Long userId,
            final String idempotencyKey,
            final List<StockMutation> successfulStockMutations,
            final DataIntegrityViolationException exception) {

        compensateStockMutations(
                successfulStockMutations,
                exception
        );

        final Order concurrentOrder = orderRepository
                .findByUserIdAndIdempotencyKey(
                        userId,
                        idempotencyKey)
                .orElseThrow(() -> exception);

        return orderMapper.toResponseDto(concurrentOrder);
    }

    @Override
    public OrderResponseDto getOrder(
            final Long userId,
            final GetOrderRequestDto request) {

        getOrderRequestValidator.validate(request);

        final Order order = orderRepository
                .findByIdAndUserId(
                        request.orderId(),
                        userId)
                .orElseThrow(
                        () -> new OrderNotFoundException(request.orderId())
                );

        return orderMapper.toResponseDto(order);
    }

    private record StockMutation(
            Long productId,
            Integer quantity,
            String operationId,
            String compensationId,
            StockOperationDirection direction) {
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void compensateStockMutations(
            final List<StockMutation> stockMutations,
            final Exception originalException) {

        stockMutations.stream()
                .forEach(mutation -> {
                    try {
                        productGrpcClient.compensateStock(
                                mutation.productId(),
                                mutation.quantity(),
                                mutation.compensationId(),
                                getCompensationDirection(mutation.direction())
                        );
                    } catch (final Exception compensationException) {
                        originalException.addSuppressed(compensationException);
                    }
                });
    }

    private StockOperationDirection getCompensationDirection(
            final StockOperationDirection originalDirection) {

       final StockOperationDirection compensationDirection;

        if (originalDirection == StockOperationDirection.DECREASE) {
            compensationDirection = StockOperationDirection.INCREASE;
        } else if (originalDirection == StockOperationDirection.INCREASE) {
            compensationDirection = StockOperationDirection.DECREASE;
        } else {
            throw new IllegalArgumentException(
                    "Unsupported stock operation direction: " + originalDirection
            );
        }

        return compensationDirection;
    }
}