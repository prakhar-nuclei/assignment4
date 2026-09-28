package com.nuclei.productcatalogservice.grpc.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import com.nuclei.product.proto.GetProductRequest;
import com.nuclei.product.proto.ProductResponse;
import com.nuclei.product.proto.UpdateStockRequest;
import com.nuclei.productcatalogservice.dto.ProductResponseDto;
import com.nuclei.productcatalogservice.exception.InsufficientStockException;
import com.nuclei.productcatalogservice.exception.InvalidStockOperationException;
import com.nuclei.productcatalogservice.exception.ProductConcurrencyException;
import com.nuclei.productcatalogservice.exception.ProductNotFoundException;
import com.nuclei.productcatalogservice.service.ProductService;
import java.math.BigDecimal;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductGrpcServiceTest {

    @Mock
    private ProductService productService;

    @Mock
    private StreamObserver<ProductResponse> responseObserver;

    private ProductGrpcService productGrpcService;

    @BeforeEach
    void setUp() {
        productGrpcService = new ProductGrpcService(productService);
    }

    @Test
    void getProductShouldReturnMappedResponseWhenProductExists() {
        ProductResponseDto product = new ProductResponseDto(
                1L,
                "Laptop",
                new BigDecimal("50000.00"),
                10
        );

        when(productService.getProduct(1L)).thenReturn(product);

        productGrpcService.getProduct(
                GetProductRequest.newBuilder()
                        .setProductId(1L)
                        .build(),
                responseObserver
        );

        var responseCaptor =
                org.mockito.ArgumentCaptor.forClass(ProductResponse.class);

        verify(responseObserver).onNext(responseCaptor.capture());
        verify(responseObserver).onCompleted();

        ProductResponse response = responseCaptor.getValue();

        assertEquals(1L, response.getProductId());
        assertEquals("Laptop", response.getName());
        assertEquals("50000.00", response.getPrice());
        assertEquals(10, response.getStock());

        verify(productService).getProduct(1L);
        verifyNoMoreInteractions(productService);
    }

    @Test
    void getProductShouldReturnInvalidArgumentWhenProductIdIsInvalid() {
        productGrpcService.getProduct(
                GetProductRequest.newBuilder()
                        .setProductId(0L)
                        .build(),
                responseObserver
        );

        verifyErrorStatus(Status.Code.INVALID_ARGUMENT);

        verify(productService, never()).getProduct(0L);
        verifyNoMoreInteractions(productService);
    }

    @Test
    void getProductShouldReturnNotFoundWhenProductDoesNotExist() {
        when(productService.getProduct(1L))
                .thenThrow(new ProductNotFoundException(1L));

        productGrpcService.getProduct(
                GetProductRequest.newBuilder()
                        .setProductId(1L)
                        .build(),
                responseObserver
        );

        verifyErrorStatus(Status.Code.NOT_FOUND);

        verify(productService).getProduct(1L);
        verifyNoMoreInteractions(productService);
    }

    @Test
    void updateStockShouldReturnMappedResponseWhenStockIsUpdated() {
        ProductResponseDto product = new ProductResponseDto(
                1L,
                "Laptop",
                new BigDecimal("50000.00"),
                15
        );

        when(productService.updateStock(1L, 5))
                .thenReturn(product);

        productGrpcService.updateStock(
                UpdateStockRequest.newBuilder()
                        .setProductId(1L)
                        .setQuantity(5)
                        .build(),
                responseObserver
        );

        var responseCaptor =
                org.mockito.ArgumentCaptor.forClass(ProductResponse.class);

        verify(responseObserver).onNext(responseCaptor.capture());
        verify(responseObserver).onCompleted();

        ProductResponse response = responseCaptor.getValue();

        assertEquals(1L, response.getProductId());
        assertEquals("Laptop", response.getName());
        assertEquals("50000.00", response.getPrice());
        assertEquals(15, response.getStock());

        verify(productService).updateStock(1L, 5);
        verifyNoMoreInteractions(productService);
    }

    @Test
    void updateStockShouldReturnInvalidArgumentWhenProductIdIsInvalid() {
        productGrpcService.updateStock(
                UpdateStockRequest.newBuilder()
                        .setProductId(0L)
                        .setQuantity(5)
                        .build(),
                responseObserver
        );

        verifyErrorStatus(Status.Code.INVALID_ARGUMENT);

        verify(productService, never()).updateStock(0L, 5);
        verifyNoMoreInteractions(productService);
    }

    @Test
    void updateStockShouldReturnInvalidArgumentWhenQuantityIsZero() {
        when(productService.updateStock(1L, 0))
                .thenThrow(new InvalidStockOperationException());

        productGrpcService.updateStock(
                UpdateStockRequest.newBuilder()
                        .setProductId(1L)
                        .setQuantity(0)
                        .build(),
                responseObserver
        );

        verifyErrorStatus(Status.Code.INVALID_ARGUMENT);

        verify(productService).updateStock(1L, 0);
        verifyNoMoreInteractions(productService);
    }

    @Test
    void updateStockShouldReturnNotFoundWhenProductDoesNotExist() {
        when(productService.updateStock(1L, 5))
                .thenThrow(new ProductNotFoundException(1L));

        productGrpcService.updateStock(
                UpdateStockRequest.newBuilder()
                        .setProductId(1L)
                        .setQuantity(5)
                        .build(),
                responseObserver
        );

        verifyErrorStatus(Status.Code.NOT_FOUND);

        verify(productService).updateStock(1L, 5);
        verifyNoMoreInteractions(productService);
    }

    @Test
    void updateStockShouldReturnFailedPreconditionWhenStockIsInsufficient() {
        when(productService.updateStock(1L, -15))
                .thenThrow(new InsufficientStockException(1L, -15));

        productGrpcService.updateStock(
                UpdateStockRequest.newBuilder()
                        .setProductId(1L)
                        .setQuantity(-15)
                        .build(),
                responseObserver
        );

        verifyErrorStatus(Status.Code.FAILED_PRECONDITION);

        verify(productService).updateStock(1L, -15);
        verifyNoMoreInteractions(productService);
    }

    @Test
    void updateStockShouldReturnAbortedWhenConcurrencyConflictOccurs() {
        when(productService.updateStock(1L, -5))
                .thenThrow(new ProductConcurrencyException(1L));

        productGrpcService.updateStock(
                UpdateStockRequest.newBuilder()
                        .setProductId(1L)
                        .setQuantity(-5)
                        .build(),
                responseObserver
        );

        verifyErrorStatus(Status.Code.ABORTED);

        verify(productService).updateStock(1L, -5);
        verifyNoMoreInteractions(productService);
    }

    @Test
    void getProductShouldPreserveExactMonetaryRepresentation() {
        ProductResponseDto product = new ProductResponseDto(
                1L,
                "Product",
                new BigDecimal("123.40"),
                5
        );

        when(productService.getProduct(1L)).thenReturn(product);

        productGrpcService.getProduct(
                GetProductRequest.newBuilder()
                        .setProductId(1L)
                        .build(),
                responseObserver
        );

        var responseCaptor =
                org.mockito.ArgumentCaptor.forClass(ProductResponse.class);

        verify(responseObserver).onNext(responseCaptor.capture());

        assertEquals("123.40", responseCaptor.getValue().getPrice());

        verify(productService).getProduct(1L);
        verifyNoMoreInteractions(productService);
    }

    @SuppressWarnings("unchecked")
    private void verifyErrorStatus(Status.Code expectedCode) {
        var errorCaptor =
                org.mockito.ArgumentCaptor.forClass(
                        StatusRuntimeException.class);

        verify(responseObserver).onError(errorCaptor.capture());

        assertEquals(
                expectedCode,
                errorCaptor.getValue().getStatus().getCode()
        );

        verify(responseObserver, never()).onNext(
                org.mockito.ArgumentMatchers.any(ProductResponse.class)
        );
        verify(responseObserver, never()).onCompleted();
    }
}