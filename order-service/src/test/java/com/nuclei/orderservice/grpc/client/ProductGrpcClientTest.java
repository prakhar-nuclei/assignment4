package com.nuclei.orderservice.grpc.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.nuclei.orderservice.dtos.ProductResponseDto;
import com.nuclei.orderservice.exception.InsufficientStockException;
import com.nuclei.orderservice.exception.ProductConcurrencyException;
import com.nuclei.orderservice.exception.ProductNotFoundException;
import com.nuclei.orderservice.mapper.ProductGrpcMapper;
import com.nuclei.product.proto.*;
import java.math.BigDecimal;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductGrpcClientTest {

    @Mock
    private ProductServiceGrpc.ProductServiceBlockingStub productServiceBlockingStub;

    @Mock
    private ProductGrpcMapper productGrpcMapper;

    private ProductGrpcClient productGrpcClient;

    @BeforeEach
    void setUp() {
        productGrpcClient = new ProductGrpcClient(
                productServiceBlockingStub,
                productGrpcMapper);
    }

    @Test
    void getProductShouldReturnMappedProductResponse() {
        final Long productId = 10L;

        final ProductResponse grpcResponse =
                ProductResponse.newBuilder()
                        .setProductId(productId)
                        .setName("Laptop")
                        .setPrice("999.99")
                        .setStock(5)
                        .build();

        final ProductResponseDto expectedResponse =
                new ProductResponseDto(
                        productId,
                        "Laptop",
                        new BigDecimal("999.99"),
                        5);

        when(productServiceBlockingStub.getProduct(any(GetProductRequest.class)))
                .thenReturn(grpcResponse);

        when(productGrpcMapper.toProductResponseDto(grpcResponse))
                .thenReturn(expectedResponse);

        final ProductResponseDto actualResponse =
                productGrpcClient.getProduct(productId);

        assertSame(expectedResponse, actualResponse);

        final ArgumentCaptor<GetProductRequest> requestCaptor =
                ArgumentCaptor.forClass(GetProductRequest.class);

        verify(productServiceBlockingStub).getProduct(requestCaptor.capture());

        assertEquals(
                productId,
                requestCaptor.getValue().getProductId());

        verify(productGrpcMapper).toProductResponseDto(grpcResponse);
    }

    @Test
    void getProductShouldThrowProductNotFoundExceptionWhenProductDoesNotExist() {
        final Long productId = 10L;

        final StatusRuntimeException grpcException =
                Status.NOT_FOUND
                        .withDescription("Product not found")
                        .asRuntimeException();

        when(productServiceBlockingStub.getProduct(any(GetProductRequest.class)))
                .thenThrow(grpcException);

        assertThrows(
                ProductNotFoundException.class,
                () -> productGrpcClient.getProduct(productId));

        verify(productServiceBlockingStub)
                .getProduct(any(GetProductRequest.class));

        verifyNoInteractions(productGrpcMapper);
    }

    @Test
    void getProductShouldPropagateGrpcExceptionWhenStatusIsNotNotFound() {
        final Long productId = 10L;

        final StatusRuntimeException grpcException =
                Status.UNAVAILABLE
                        .withDescription("Product service unavailable")
                        .asRuntimeException();

        when(productServiceBlockingStub.getProduct(any(GetProductRequest.class)))
                .thenThrow(grpcException);

        final StatusRuntimeException exception = assertThrows(
                StatusRuntimeException.class,
                () -> productGrpcClient.getProduct(productId));

        assertSame(grpcException, exception);

        verify(productServiceBlockingStub)
                .getProduct(any(GetProductRequest.class));

        verifyNoInteractions(productGrpcMapper);
    }

    @Test
    void updateStockShouldReturnMappedProductResponse() {
        final Long productId = 10L;
        final Integer quantity = 2;
        final String operationId = "operation-123";

        final UpdateStockRequest expectedRequest =
                UpdateStockRequest.newBuilder()
                        .setProductId(productId)
                        .setQuantity(quantity)
                        .setOperationId(operationId)
                        .build();

        final ProductResponse grpcResponse =
                ProductResponse.newBuilder()
                        .setProductId(productId)
                        .setName("Laptop")
                        .setPrice("999.99")
                        .setStock(8)
                        .build();

        final ProductResponseDto expectedResponse =
                new ProductResponseDto(
                        productId,
                        "Laptop",
                        new BigDecimal("999.99"),
                        8);

        when(productServiceBlockingStub.updateStock(any(UpdateStockRequest.class)))
                .thenReturn(grpcResponse);

        when(productGrpcMapper.toProductResponseDto(grpcResponse))
                .thenReturn(expectedResponse);

        final ProductResponseDto actualResponse =
                productGrpcClient.updateStock(
                        productId,
                        quantity,
                        operationId);

        assertSame(expectedResponse, actualResponse);

        final ArgumentCaptor<UpdateStockRequest> requestCaptor =
                ArgumentCaptor.forClass(UpdateStockRequest.class);

        verify(productServiceBlockingStub)
                .updateStock(requestCaptor.capture());

        final UpdateStockRequest actualRequest =
                requestCaptor.getValue();

        assertEquals(productId, actualRequest.getProductId());
        assertEquals(quantity, actualRequest.getQuantity());
        assertEquals(operationId, actualRequest.getOperationId());

        verify(productGrpcMapper)
                .toProductResponseDto(grpcResponse);
    }

    @Test
    void updateStockShouldThrowProductNotFoundExceptionWhenProductDoesNotExist() {
        final Long productId = 10L;
        final Integer quantity = 2;
        final String operationId = "operation-123";

        final StatusRuntimeException grpcException =
                Status.NOT_FOUND
                        .withDescription("Product not found")
                        .asRuntimeException();

        when(productServiceBlockingStub.updateStock(
                any(UpdateStockRequest.class)))
                .thenThrow(grpcException);

        assertThrows(
                ProductNotFoundException.class,
                () -> productGrpcClient.updateStock(
                        productId,
                        quantity,
                        operationId));

        verify(productServiceBlockingStub)
                .updateStock(any(UpdateStockRequest.class));

        verifyNoInteractions(productGrpcMapper);
    }

    @Test
    void updateStockShouldThrowInsufficientStockExceptionWhenStockIsInsufficient() {
        final Long productId = 10L;
        final Integer quantity = 20;
        final String operationId = "operation-123";

        final StatusRuntimeException grpcException =
                Status.FAILED_PRECONDITION
                        .withDescription("Insufficient stock")
                        .asRuntimeException();

        when(productServiceBlockingStub.updateStock(
                any(UpdateStockRequest.class)))
                .thenThrow(grpcException);

        assertThrows(
                InsufficientStockException.class,
                () -> productGrpcClient.updateStock(
                        productId,
                        quantity,
                        operationId));

        verify(productServiceBlockingStub)
                .updateStock(any(UpdateStockRequest.class));

        verifyNoInteractions(productGrpcMapper);
    }

    @Test
    void updateStockShouldThrowProductConcurrencyExceptionWhenConcurrencyOccurs() {
        final Long productId = 10L;
        final Integer quantity = 2;
        final String operationId = "operation-123";

        final StatusRuntimeException grpcException =
                Status.ABORTED
                        .withDescription("Concurrent stock update")
                        .asRuntimeException();

        when(productServiceBlockingStub.updateStock(
                any(UpdateStockRequest.class)))
                .thenThrow(grpcException);

        assertThrows(
                ProductConcurrencyException.class,
                () -> productGrpcClient.updateStock(
                        productId,
                        quantity,
                        operationId));

        verify(productServiceBlockingStub)
                .updateStock(any(UpdateStockRequest.class));

        verifyNoInteractions(productGrpcMapper);
    }

    @Test
    void updateStockShouldPropagateGrpcExceptionWhenStatusIsNotMapped() {
        final Long productId = 10L;
        final Integer quantity = 2;
        final String operationId = "operation-123";

        final StatusRuntimeException grpcException =
                Status.UNAVAILABLE
                        .withDescription("Product service unavailable")
                        .asRuntimeException();

        when(productServiceBlockingStub.updateStock(
                any(UpdateStockRequest.class)))
                .thenThrow(grpcException);

        final StatusRuntimeException exception = assertThrows(
                StatusRuntimeException.class,
                () -> productGrpcClient.updateStock(
                        productId,
                        quantity,
                        operationId));

        assertSame(grpcException, exception);

        verify(productServiceBlockingStub)
                .updateStock(any(UpdateStockRequest.class));

        verifyNoInteractions(productGrpcMapper);
    }

    @Test
    void compensateStockShouldThrowProductNotFoundExceptionWhenProductDoesNotExist() {
        final Long productId = 10L;
        final Integer quantity = -2;
        final String compensationId = "compensation-123";

        final StatusRuntimeException grpcException =
                Status.NOT_FOUND
                        .withDescription("Product not found")
                        .asRuntimeException();

        when(productServiceBlockingStub.compensateStock(
                any(CompensateStockRequest.class)))
                .thenThrow(grpcException);

        assertThrows(
                ProductNotFoundException.class,
                () -> productGrpcClient.compensateStock(
                        productId,
                        quantity,
                        compensationId));

        verify(productServiceBlockingStub)
                .compensateStock(any(CompensateStockRequest.class));

        verifyNoInteractions(productGrpcMapper);
    }

    @Test
    void compensateStockShouldThrowInsufficientStockExceptionWhenStockIsInsufficient() {
        final Long productId = 10L;
        final Integer quantity = -2;
        final String compensationId = "compensation-123";

        final StatusRuntimeException grpcException =
                Status.FAILED_PRECONDITION
                        .withDescription("Insufficient stock")
                        .asRuntimeException();

        when(productServiceBlockingStub.compensateStock(
                any(CompensateStockRequest.class)))
                .thenThrow(grpcException);

        assertThrows(
                InsufficientStockException.class,
                () -> productGrpcClient.compensateStock(
                        productId,
                        quantity,
                        compensationId));

        verify(productServiceBlockingStub)
                .compensateStock(any(CompensateStockRequest.class));

        verifyNoInteractions(productGrpcMapper);
    }

    @Test
    void compensateStockShouldThrowProductConcurrencyExceptionWhenConcurrencyOccurs() {
        final Long productId = 10L;
        final Integer quantity = -2;
        final String compensationId = "compensation-123";

        final StatusRuntimeException grpcException =
                Status.ABORTED
                        .withDescription("Concurrent stock update")
                        .asRuntimeException();

        when(productServiceBlockingStub.compensateStock(
                any(CompensateStockRequest.class)))
                .thenThrow(grpcException);

        assertThrows(
                ProductConcurrencyException.class,
                () -> productGrpcClient.compensateStock(
                        productId,
                        quantity,
                        compensationId));

        verify(productServiceBlockingStub)
                .compensateStock(any(CompensateStockRequest.class));

        verifyNoInteractions(productGrpcMapper);
    }

    @Test
    void compensateStockShouldPropagateGrpcExceptionWhenStatusIsNotMapped() {
        final Long productId = 10L;
        final Integer quantity = -2;
        final String compensationId = "compensation-123";

        final StatusRuntimeException grpcException =
                Status.UNAVAILABLE
                        .withDescription("Product service unavailable")
                        .asRuntimeException();

        when(productServiceBlockingStub.compensateStock(
                any(CompensateStockRequest.class)))
                .thenThrow(grpcException);

        final StatusRuntimeException exception = assertThrows(
                StatusRuntimeException.class,
                () -> productGrpcClient.compensateStock(
                        productId,
                        quantity,
                        compensationId));

        assertSame(grpcException, exception);

        verify(productServiceBlockingStub)
                .compensateStock(any(CompensateStockRequest.class));

        verifyNoInteractions(productGrpcMapper);
    }
}
