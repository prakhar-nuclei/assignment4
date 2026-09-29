package com.nuclei.productcatalogservice.grpc.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import com.nuclei.product.proto.CreateProductRequest;
import com.nuclei.product.proto.DeleteProductRequest;
import com.nuclei.product.proto.GetProductRequest;
import com.nuclei.product.proto.ProductResponse;
import com.nuclei.product.proto.UpdateProductRequest;
import com.nuclei.product.proto.UpdateStockRequest;
import com.nuclei.productcatalogservice.dto.ProductResponseDto;
import com.nuclei.productcatalogservice.exception.InsufficientStockException;
import com.nuclei.productcatalogservice.exception.InvalidStockOperationException;
import com.nuclei.productcatalogservice.exception.ProductConcurrencyException;
import com.nuclei.productcatalogservice.exception.ProductNotFoundException;
import com.nuclei.productcatalogservice.mapper.ProductGrpcMapper;
import com.nuclei.productcatalogservice.service.ProductService;
import java.math.BigDecimal;
import com.google.protobuf.Empty;
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
    private ProductGrpcMapper productGrpcMapper;

    @Mock
    private StreamObserver<ProductResponse> responseObserver;

    @Mock
    private StreamObserver<Empty> deleteResponseObserver;

    private ProductGrpcService productGrpcService;

    @BeforeEach
    void setUp() {
        productGrpcService = new ProductGrpcService(
                productService,
                productGrpcMapper
        );
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

        ProductResponse productResponse = ProductResponse.newBuilder()
                .setProductId(1L)
                .setName("Laptop")
                .setPrice("50000.00")
                .setStock(10)
                .build();

        when(productGrpcMapper.toProductResponse(product))
                .thenReturn(productResponse);

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
        verify(productGrpcMapper).toProductResponse(product);
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
    void createProductShouldReturnMappedResponseWhenProductIsCreated() {
        ProductResponseDto product = new ProductResponseDto(
                1L,
                "Laptop",
                new BigDecimal("50000.00"),
                10
        );

        ProductResponse productResponse = ProductResponse.newBuilder()
                .setProductId(1L)
                .setName("Laptop")
                .setPrice("50000.00")
                .setStock(10)
                .build();

        when(productService.createProduct(
                "Laptop",
                new BigDecimal("50000.00"),
                10
        )).thenReturn(product);

        when(productGrpcMapper.toProductResponse(product))
                .thenReturn(productResponse);

        productGrpcService.createProduct(
                CreateProductRequest.newBuilder()
                        .setName("Laptop")
                        .setPrice("50000.00")
                        .setStock(10)
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

        verify(productService).createProduct(
                "Laptop",
                new BigDecimal("50000.00"),
                10
        );
        verify(productGrpcMapper).toProductResponse(product);
        verifyNoMoreInteractions(productService);
    }

    @Test
    void createProductShouldReturnInvalidArgumentWhenPriceIsInvalid() {
        productGrpcService.createProduct(
                CreateProductRequest.newBuilder()
                        .setName("Laptop")
                        .setPrice("invalid-price")
                        .setStock(10)
                        .build(),
                responseObserver
        );

        verifyErrorStatus(Status.Code.INVALID_ARGUMENT);

        verifyNoMoreInteractions(productService);
    }

    @Test
    void updateProductShouldReturnMappedResponseWhenProductIsUpdated() {
        ProductResponseDto product = new ProductResponseDto(
                1L,
                "Gaming Laptop",
                new BigDecimal("75000.00"),
                20
        );

        ProductResponse productResponse = ProductResponse.newBuilder()
                .setProductId(1L)
                .setName("Gaming Laptop")
                .setPrice("75000.00")
                .setStock(20)
                .build();

        when(productService.updateProduct(
                1L,
                "Gaming Laptop",
                new BigDecimal("75000.00"),
                20
        )).thenReturn(product);

        when(productGrpcMapper.toProductResponse(product))
                .thenReturn(productResponse);

        productGrpcService.updateProduct(
                UpdateProductRequest.newBuilder()
                        .setProductId(1L)
                        .setName("Gaming Laptop")
                        .setPrice("75000.00")
                        .setStock(20)
                        .build(),
                responseObserver
        );

        var responseCaptor =
                org.mockito.ArgumentCaptor.forClass(ProductResponse.class);

        verify(responseObserver).onNext(responseCaptor.capture());
        verify(responseObserver).onCompleted();

        ProductResponse response = responseCaptor.getValue();

        assertEquals(1L, response.getProductId());
        assertEquals("Gaming Laptop", response.getName());
        assertEquals("75000.00", response.getPrice());
        assertEquals(20, response.getStock());

        verify(productService).updateProduct(
                1L,
                "Gaming Laptop",
                new BigDecimal("75000.00"),
                20
        );
        verify(productGrpcMapper).toProductResponse(product);
        verifyNoMoreInteractions(productService);
    }

    @Test
    void updateProductShouldReturnInvalidArgumentWhenProductIdIsInvalid() {
        productGrpcService.updateProduct(
                UpdateProductRequest.newBuilder()
                        .setProductId(0L)
                        .setName("Gaming Laptop")
                        .setPrice("75000.00")
                        .setStock(20)
                        .build(),
                responseObserver
        );

        verifyErrorStatus(Status.Code.INVALID_ARGUMENT);

        verifyNoMoreInteractions(productService);
    }

    @Test
    void updateProductShouldReturnInvalidArgumentWhenPriceIsInvalid() {
        productGrpcService.updateProduct(
                UpdateProductRequest.newBuilder()
                        .setProductId(1L)
                        .setName("Gaming Laptop")
                        .setPrice("invalid-price")
                        .setStock(20)
                        .build(),
                responseObserver
        );

        verifyErrorStatus(Status.Code.INVALID_ARGUMENT);

        verifyNoMoreInteractions(productService);
    }

    @Test
    void updateProductShouldReturnNotFoundWhenProductDoesNotExist() {
        when(productService.updateProduct(
                1L,
                "Gaming Laptop",
                new BigDecimal("75000.00"),
                20
        )).thenThrow(new ProductNotFoundException(1L));

        productGrpcService.updateProduct(
                UpdateProductRequest.newBuilder()
                        .setProductId(1L)
                        .setName("Gaming Laptop")
                        .setPrice("75000.00")
                        .setStock(20)
                        .build(),
                responseObserver
        );

        verifyErrorStatus(Status.Code.NOT_FOUND);

        verify(productService).updateProduct(
                1L,
                "Gaming Laptop",
                new BigDecimal("75000.00"),
                20
        );
        verifyNoMoreInteractions(productService);
    }

    @Test
    void updateProductShouldReturnAbortedWhenConcurrencyConflictOccurs() {
        when(productService.updateProduct(
                1L,
                "Gaming Laptop",
                new BigDecimal("75000.00"),
                20
        )).thenThrow(new ProductConcurrencyException(1L));

        productGrpcService.updateProduct(
                UpdateProductRequest.newBuilder()
                        .setProductId(1L)
                        .setName("Gaming Laptop")
                        .setPrice("75000.00")
                        .setStock(20)
                        .build(),
                responseObserver
        );

        verifyErrorStatus(Status.Code.ABORTED);

        verify(productService).updateProduct(
                1L,
                "Gaming Laptop",
                new BigDecimal("75000.00"),
                20
        );
        verifyNoMoreInteractions(productService);
    }

    @Test
    void deleteProductShouldReturnEmptyWhenProductIsDeleted() {
        productGrpcService.deleteProduct(
                DeleteProductRequest.newBuilder()
                        .setProductId(1L)
                        .build(),
                deleteResponseObserver
        );

        verify(productService).deleteProduct(1L);
        verify(deleteResponseObserver).onNext(Empty.getDefaultInstance());
        verify(deleteResponseObserver).onCompleted();

        verifyNoMoreInteractions(productService);
    }

    @Test
    void deleteProductShouldReturnInvalidArgumentWhenProductIdIsInvalid() {
        productGrpcService.deleteProduct(
                DeleteProductRequest.newBuilder()
                        .setProductId(0L)
                        .build(),
                deleteResponseObserver
        );

        verifyEmptyErrorStatus(Status.Code.INVALID_ARGUMENT);

        verify(productService, never()).deleteProduct(0L);
        verifyNoMoreInteractions(productService);
    }

    @Test
    void deleteProductShouldReturnNotFoundWhenProductDoesNotExist() {
        doThrow(new ProductNotFoundException(1L))
                .when(productService)
                .deleteProduct(1L);

        productGrpcService.deleteProduct(
                DeleteProductRequest.newBuilder()
                        .setProductId(1L)
                        .build(),
                deleteResponseObserver
        );

        verifyEmptyErrorStatus(Status.Code.NOT_FOUND);

        verify(productService).deleteProduct(1L);
        verifyNoMoreInteractions(productService);
    }

    @Test
    void deleteProductShouldReturnAbortedWhenConcurrencyConflictOccurs() {
        doThrow(new ProductConcurrencyException(1L))
                .when(productService)
                .deleteProduct(1L);;

        productGrpcService.deleteProduct(
                DeleteProductRequest.newBuilder()
                        .setProductId(1L)
                        .build(),
                deleteResponseObserver
        );

        verifyEmptyErrorStatus(Status.Code.ABORTED);

        verify(productService).deleteProduct(1L);
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

        ProductResponse productResponse = ProductResponse.newBuilder()
                .setProductId(1L)
                .setName("Laptop")
                .setPrice("50000.00")
                .setStock(15)
                .build();

        when(productGrpcMapper.toProductResponse(product))
                .thenReturn(productResponse);

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
        verify(productGrpcMapper).toProductResponse(product);
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

        ProductResponse productResponse = ProductResponse.newBuilder()
                .setProductId(1L)
                .setName("Product")
                .setPrice("123.40")
                .setStock(5)
                .build();

        when(productGrpcMapper.toProductResponse(product))
                .thenReturn(productResponse);

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
        verify(productGrpcMapper).toProductResponse(product);
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

    @SuppressWarnings("unchecked")
    private void verifyEmptyErrorStatus(Status.Code expectedCode) {
        var errorCaptor =
                org.mockito.ArgumentCaptor.forClass(
                        StatusRuntimeException.class);

        verify(deleteResponseObserver).onError(errorCaptor.capture());

        assertEquals(
                expectedCode,
                errorCaptor.getValue().getStatus().getCode()
        );

        verify(deleteResponseObserver, never())
                .onNext(Empty.getDefaultInstance());

        verify(deleteResponseObserver, never()).onCompleted();
    }
}