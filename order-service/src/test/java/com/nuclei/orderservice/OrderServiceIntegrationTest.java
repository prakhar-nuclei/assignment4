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
import com.nuclei.orderservice.dtos.ProductResponseDto;
import com.nuclei.orderservice.dtos.UserResponseDto;
import com.nuclei.orderservice.grpc.client.ProductGrpcClient;
import com.nuclei.orderservice.grpc.client.UserGrpcClient;
import com.nuclei.orderservice.grpc.metadata.GrpcMetadataConstants;
import com.nuclei.orderservice.repo.OrderRepository;
import java.math.BigDecimal;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.ForwardingClientCall;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
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

    @MockitoBean
    private UserGrpcClient userGrpcClient;

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
    }

    @Test
    void createOrderShouldCreateOrderThroughGrpcEndpoint() {
        final Long userId = 1L;
        final Long productId = 100L;

        mockUser(userId);
        mockProduct(productId);
        mockStockUpdate(productId, 2);

        final OrderResponse response =
                createAuthenticatedStub(userId)
                        .createOrder(createOrderRequest(
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

        mockUser(userId);
        mockProduct(productId);
        mockStockUpdate(productId, 2);

        final OrderServiceGrpc.OrderServiceBlockingStub stub =
                createAuthenticatedStub(userId);

        final CreateOrderRequest request =
                createOrderRequest(idempotencyKey, productId, 2);

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

        verify(userGrpcClient, times(1)).getUser(userId);
        verify(productGrpcClient, times(1)).getProduct(productId);
        verify(productGrpcClient, times(1))
                .updateStock(eq(productId), eq(2), anyString());
    }

    @Test
    void getOrderShouldReturnPersistedOrderThroughGrpcEndpoint() {
        final Long userId = 1L;
        final Long productId = 100L;

        mockUser(userId);
        mockProduct(productId);
        mockStockUpdate(productId, 2);

        final OrderServiceGrpc.OrderServiceBlockingStub stub =
                createAuthenticatedStub(userId);

        final OrderResponse createdResponse =
                stub.createOrder(
                        createOrderRequest(
                                "integration-get-order",
                                productId,
                                2));

        final OrderResponse getResponse =
                stub.getOrder(
                        GetOrderRequest.newBuilder()
                                .setOrderId(createdResponse.getOrderId())
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
                createAuthenticatedStub(userId);

        final StatusRuntimeException exception =
                assertThrows(
                        StatusRuntimeException.class,
                        () -> stub.getOrder(
                                GetOrderRequest.newBuilder()
                                        .setOrderId(999999L)
                                        .build()));

        assertEquals(
                Status.Code.NOT_FOUND,
                exception.getStatus().getCode());
    }

    @Test
    void createOrderShouldRejectInvalidRequest() {
        final Long userId = 1L;

        final OrderServiceGrpc.OrderServiceBlockingStub stub =
                createAuthenticatedStub(userId);

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
                createAuthenticatedStub(userId);

        final StatusRuntimeException exception =
                assertThrows(
                        StatusRuntimeException.class,
                        () -> stub.getOrder(
                                GetOrderRequest.newBuilder()
                                        .setOrderId(0L)
                                        .build()));

        assertEquals(
                Status.Code.INVALID_ARGUMENT,
                exception.getStatus().getCode());
    }

    @Test
    void createOrderShouldRejectMissingAuthentication() {
        final OrderServiceGrpc.OrderServiceBlockingStub stub =
                OrderServiceGrpc.newBlockingStub(channel);

        final CreateOrderRequest request =
                createOrderRequest(
                        "missing-auth-order",
                        100L,
                        2);

        final StatusRuntimeException exception =
                assertThrows(
                        StatusRuntimeException.class,
                        () -> stub.createOrder(request));

        assertEquals(
                Status.Code.UNAUTHENTICATED,
                exception.getStatus().getCode());

        assertEquals(0, orderRepository.count());
    }

    @Test
    void getOrderShouldRejectMissingAuthentication() {
        final OrderServiceGrpc.OrderServiceBlockingStub stub =
                OrderServiceGrpc.newBlockingStub(channel);

        final StatusRuntimeException exception =
                assertThrows(
                        StatusRuntimeException.class,
                        () -> stub.getOrder(
                                GetOrderRequest.newBuilder()
                                        .setOrderId(1L)
                                        .build()));

        assertEquals(
                Status.Code.UNAUTHENTICATED,
                exception.getStatus().getCode());
    }

    private OrderServiceGrpc.OrderServiceBlockingStub createAuthenticatedStub(
            final Long userId) {

        final ClientInterceptor metadataInterceptor =
                createUserMetadataInterceptor(userId);

        return OrderServiceGrpc.newBlockingStub(
                ClientInterceptors.intercept(
                        channel,
                        metadataInterceptor));
    }

    private ClientInterceptor createUserMetadataInterceptor(
            final Long userId) {

        return new ClientInterceptor() {
            @Override
            public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                    final MethodDescriptor<ReqT, RespT> method,
                    final CallOptions callOptions,
                    final Channel next) {

                final ClientCall<ReqT, RespT> call =
                        next.newCall(method, callOptions);

                return new ForwardingClientCall
                        .SimpleForwardingClientCall<>(call) {

                    @Override
                    public void start(
                            final Listener<RespT> responseListener,
                            final Metadata headers) {

                        headers.put(
                                GrpcMetadataConstants.AUTHENTICATED_USER_ID,
                                String.valueOf(userId));

                        super.start(
                                responseListener,
                                headers);
                    }
                };
            }
        };
    }

    private void mockUser(final Long userId) {
        when(userGrpcClient.getUser(userId))
                .thenReturn(
                        new UserResponseDto(
                                userId,
                                "Prakhar",
                                "prakhar@example.com"));
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
            final String idempotencyKey,
            final Long productId,
            final Integer quantity) {

        return CreateOrderRequest.newBuilder()
                .setIdempotencyKey(idempotencyKey)
                .addItems(
                        OrderItemRequest.newBuilder()
                                .setProductId(productId)
                                .setQuantity(quantity)
                                .build())
                .build();
    }
}