package com.nuclei.orderservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import com.nuclei.order.proto.CreateOrderRequest;
import com.nuclei.order.proto.GetOrderRequest;
import com.nuclei.order.proto.OrderItemRequest;
import com.nuclei.order.proto.OrderResponse;
import com.nuclei.order.proto.OrderServiceGrpc;
import com.nuclei.orderservice.cache.OrderIdempotencyCache;
import com.nuclei.orderservice.dtos.ProductResponseDto;
import com.nuclei.orderservice.grpc.client.ProductGrpcClient;
import com.nuclei.orderservice.grpc.server.OrderGrpcService;
import com.nuclei.orderservice.repo.OrderRepository;
import com.nuclei.user.proto.UserResponse;
import java.math.BigDecimal;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;


@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("test")
class OrderServiceIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderIdempotencyCache orderIdempotencyCache;

    @MockitoBean
    private ProductGrpcClient productGrpcClient;

    private static ManagedChannel channel;

    @BeforeAll
    static void setUpChannel() {
        channel = ManagedChannelBuilder
                .forAddress("localhost", 6567)
                .usePlaintext()
                .build();
    }

    @AfterAll
    static void tearDownChannel() {
        channel.shutdownNow();
    }


    @BeforeEach
    void cleanDatabase() {
        orderRepository.deleteAll();

        orderIdempotencyCache.evict(
                1L, "integration-order-1");
        orderIdempotencyCache.evict(
                1L, "integration-idempotent-order");
        orderIdempotencyCache.evict(
                1L, "integration-get-order");
    }


    @Test
    void createOrderShouldCreateOrderThroughGrpcEndpoint() {
        final Long userId = 1L;
        final Long productId = 100L;

        mockProduct(productId);
        mockStockUpdate(productId, 2);

        final OrderResponse response =
                OrderServiceGrpc.newBlockingStub(channel)
                        .createOrder(createOrderRequest(
                                userId,
                                "integration-order-1",
                                productId,
                                2));

        assertEquals(userId, response.getUserId());
        assertEquals("200.00", response.getTotalAmount());
        assertEquals("CONFIRMED", response.getStatus());
        assertEquals(1, response.getItemsCount());
        assertEquals(productId, response.getItems(0).getProductId());
        assertEquals("Laptop", response.getItems(0).getProductName());
        assertEquals("100.00", response.getItems(0).getUnitPrice());
        assertEquals(2, response.getItems(0).getQuantity());
        assertEquals("200.00", response.getItems(0).getSubtotal());

        assertEquals(1, orderRepository.count());
    }

    @Test
    void createOrderShouldReturnExistingOrderForSameIdempotencyKey() {
        final Long userId = 1L;
        final Long productId = 100L;
        final String idempotencyKey = "integration-idempotent-order";

        mockProduct(productId);
        mockStockUpdate(productId, 2);

        final OrderServiceGrpc.OrderServiceBlockingStub stub =
                OrderServiceGrpc.newBlockingStub(channel);

        final CreateOrderRequest request =
                createOrderRequest(userId,idempotencyKey, productId, 2);

        final OrderResponse firstResponse =
                stub.createOrder(request);

        final OrderResponse secondResponse =
                stub.createOrder(request);

        assertEquals(
                firstResponse.getOrderId(),
                secondResponse.getOrderId());

        assertEquals(
                firstResponse.getTotalAmount(),
                secondResponse.getTotalAmount());

        assertEquals(
                firstResponse.getStatus(),
                secondResponse.getStatus());

        assertEquals(1, orderRepository.count());

        verify(productGrpcClient, times(1)).getProduct(productId);
        verify(productGrpcClient, times(1))
                .updateStock(eq(productId), eq(2), anyString());
    }

    @Test
    void getOrderShouldReturnPersistedOrderThroughGrpcEndpoint() {
        final Long userId = 1L;
        final Long productId = 100L;

        mockProduct(productId);
        mockStockUpdate(productId, 2);

        final OrderServiceGrpc.OrderServiceBlockingStub stub =
                OrderServiceGrpc.newBlockingStub(channel);

        final OrderResponse createdResponse =
                stub.createOrder(
                        createOrderRequest(
                                    userId,
                                "integration-get-order",
                                productId,
                                2));

        final OrderResponse getResponse =
                stub.getOrder(
                        GetOrderRequest.newBuilder()
                                .setOrderId(createdResponse.getOrderId())
                                .setUserId(userId)
                                .build());

        assertEquals(
                createdResponse.getOrderId(),
                getResponse.getOrderId());

        assertEquals(userId, getResponse.getUserId());
        assertEquals("200.00", getResponse.getTotalAmount());
        assertEquals("CONFIRMED", getResponse.getStatus());
        assertEquals(1, getResponse.getItemsCount());
    }

    @Test
    void getOrderShouldReturnNotFoundForUnknownOrder() {
        final Long userId = 1L;

        final OrderServiceGrpc.OrderServiceBlockingStub stub =
                OrderServiceGrpc.newBlockingStub(channel);

        final StatusRuntimeException exception =
                assertThrows(
                        StatusRuntimeException.class,
                        () -> stub.getOrder(
                                GetOrderRequest.newBuilder()
                                        .setOrderId(999999L)
                                        .setUserId(userId)
                                        .build()));

        assertEquals(
                Status.Code.NOT_FOUND,
                exception.getStatus().getCode());
    }

    @Test
    void createOrderShouldRejectInvalidRequest() {

        final OrderServiceGrpc.OrderServiceBlockingStub stub =
                OrderServiceGrpc.newBlockingStub(channel);

        final CreateOrderRequest request =
                CreateOrderRequest.newBuilder()
                        .setIdempotencyKey("")
                        .build();

        final StatusRuntimeException exception =
                assertThrows(
                        StatusRuntimeException.class,
                        () -> stub.createOrder(request));

        assertEquals(
                Status.Code.INVALID_ARGUMENT,
                exception.getStatus().getCode());

        assertEquals(0, orderRepository.count());
    }

    @Test
    void getOrderShouldRejectInvalidRequest() {
        final Long userId = 1L;

        final OrderServiceGrpc.OrderServiceBlockingStub stub =
                OrderServiceGrpc.newBlockingStub(channel);

        final StatusRuntimeException exception =
                assertThrows(
                        StatusRuntimeException.class,
                        () -> stub.getOrder(
                                GetOrderRequest.newBuilder()
                                        .setOrderId(0L)
                                        .setUserId(userId)
                                        .build()));

        assertEquals(
                Status.Code.INVALID_ARGUMENT,
                exception.getStatus().getCode());
    }

    private void mockProduct(final Long productId) {
        when(productGrpcClient.getProduct(productId))
                .thenReturn(
                        new ProductResponseDto(
                                productId,
                                "Laptop",
                                new BigDecimal("100.00"),
                                10));
    }

    private void mockStockUpdate(
            final Long productId,
            final Integer quantity) {

        when(productGrpcClient.updateStock(
                eq(productId),
                eq(quantity),
                anyString()))
                .thenReturn(
                        new ProductResponseDto(
                                productId,
                                "Laptop",
                                new BigDecimal("100.00"),
                                8));
    }

    private CreateOrderRequest createOrderRequest(
            final Long userId,
            final String idempotencyKey,
            final Long productId,
            final Integer quantity) {

        return CreateOrderRequest.newBuilder()
                .setIdempotencyKey(idempotencyKey)
                .setUser(
                        UserResponse.newBuilder()
                                .setUserId(userId)
                                .setName("Prakhar")
                                .setEmail("prakhar@example.com")
                                .build())
                .addItems(
                        OrderItemRequest.newBuilder()
                                .setProductId(productId)
                                .setQuantity(quantity)
                                .build())
                .build();
    }
}