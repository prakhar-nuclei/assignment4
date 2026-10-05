package com.nuclei.orderservice.service.impl;

import com.nuclei.orderservice.dtos.CreateOrderRequestDto;
import com.nuclei.orderservice.dtos.GetOrderRequestDto;
import com.nuclei.orderservice.dtos.OrderItemRequestDto;
import com.nuclei.orderservice.dtos.OrderResponseDto;
import com.nuclei.orderservice.dtos.ProductResponseDto;
import com.nuclei.orderservice.entity.Order;
import com.nuclei.orderservice.entity.OrderItem;
import com.nuclei.orderservice.enums.OrderStatusEnum;
import com.nuclei.orderservice.exception.OrderNotFoundException;
import com.nuclei.orderservice.grpc.client.ProductGrpcClient;
import com.nuclei.orderservice.grpc.client.UserGrpcClient;
import com.nuclei.orderservice.mapper.OrderMapper;
import com.nuclei.orderservice.repo.OrderRepository;
import com.nuclei.orderservice.service.OrderService;
import com.nuclei.orderservice.validator.CreateOrderRequestValidator;
import com.nuclei.orderservice.validator.GetOrderRequestValidator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;


@Service
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final UserGrpcClient userGrpcClient;
    private final ProductGrpcClient productGrpcClient;
    private final CreateOrderRequestValidator createOrderRequestValidator;
    private final GetOrderRequestValidator getOrderRequestValidator;

    public OrderServiceImpl(
            final OrderRepository orderRepository,
            final OrderMapper orderMapper,
            final UserGrpcClient userGrpcClient,
            final ProductGrpcClient productGrpcClient,
            final CreateOrderRequestValidator createOrderRequestValidator,
            final GetOrderRequestValidator getOrderRequestValidator) {
        this.orderRepository = orderRepository;
        this.orderMapper = orderMapper;
        this.userGrpcClient = userGrpcClient;
        this.productGrpcClient = productGrpcClient;
        this.createOrderRequestValidator = createOrderRequestValidator;
        this.getOrderRequestValidator = getOrderRequestValidator;
    }

    @Override
    public OrderResponseDto createOrder(
            final Long userId,
            final CreateOrderRequestDto request) {

        createOrderRequestValidator.validate(request);

        final Order existingOrder = orderRepository
                .findByUserIdAndIdempotencyKey(
                        userId,
                        request.idempotencyKey())
                .orElse(null);

        final OrderResponseDto response;

        if (existingOrder != null) {
            response = orderMapper.toResponseDto(existingOrder);
        } else {
            response = createNewOrder(userId, request);
        }

        return response;
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private OrderResponseDto createNewOrder(
            final Long userId,
            final CreateOrderRequestDto request) {

        userGrpcClient.getUser(userId);

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

            final String operationId = UUID.nameUUIDFromBytes(
                    (userId + ":" + request.idempotencyKey() + ":"
                            + itemIndex)
                            .getBytes(StandardCharsets.UTF_8)
            ).toString();

            final String compensationId = UUID.nameUUIDFromBytes(
                    (operationId + ":compensation")
                            .getBytes(StandardCharsets.UTF_8)
            ).toString();

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
                            compensationId
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

        final OrderItem orderItem = new OrderItem();
        orderItem.setProductId(product.productId());
        orderItem.setProductName(product.name());
        orderItem.setUnitPrice(unitPrice);
        orderItem.setQuantity(item.quantity());
        orderItem.setSubtotal(subtotal);

        return orderItem;
    }

    private Order buildOrder(
            final Long userId,
            final String idempotencyKey,
            final List<OrderItem> orderItems,
            final BigDecimal totalAmount) {

        final Order order = new Order();
        order.setUserId(userId);
        order.setIdempotencyKey(idempotencyKey);
        order.setTotalAmount(totalAmount);
        order.setOrderStatusEnum(OrderStatusEnum.CONFIRMED);

        for (final OrderItem orderItem : orderItems) {
            orderItem.setOrder(order);
        }

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
            String compensationId) {
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void compensateStockMutations(
            final List<StockMutation> stockMutations,
            final Exception originalException) {

        for (final StockMutation mutation : stockMutations) {
            try {
                productGrpcClient.compensateStock(
                        mutation.productId(),
                        -mutation.quantity(),
                        mutation.compensationId()
                );
            } catch (final Exception compensationException) {
                originalException.addSuppressed(compensationException);
            }
        }
    }
}