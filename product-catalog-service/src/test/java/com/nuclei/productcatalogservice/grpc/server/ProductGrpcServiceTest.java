package com.nuclei.productcatalogservice.grpc.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import com.nuclei.product.proto.CompensateStockRequest;
import com.nuclei.product.proto.CreateProductRequest;
import com.nuclei.product.proto.DeleteProductRequest;
import com.nuclei.product.proto.GetProductRequest;
import com.nuclei.product.proto.ProductResponse;
import com.nuclei.product.proto.UpdateProductRequest;
import com.nuclei.product.proto.UpdateStockRequest;
import com.nuclei.productcatalogservice.dto.ProductResponseDto;
import com.nuclei.productcatalogservice.exception.InsufficientStockException;
import com.nuclei.productcatalogservice.exception.InvalidProductRequestException;
import com.nuclei.productcatalogservice.exception.InvalidStockOperationException;
import com.nuclei.productcatalogservice.exception.ProductConcurrencyException;
import com.nuclei.productcatalogservice.exception.ProductNotFoundException;
import com.nuclei.productcatalogservice.mapper.ProductGrpcMapper;
import com.nuclei.productcatalogservice.service.ProductService;
import com.nuclei.productcatalogservice.validator.ProductRequestValidator;
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

    @Mock
    private ProductRequestValidator productRequestValidator;

    private ProductGrpcService productGrpcService;

    @BeforeEach
    void setUp() {
        productGrpcService = new ProductGrpcService(
                productService,
                productGrpcMapper,
                productRequestValidator
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
        GetProductRequest request = GetProductRequest.newBuilder()
                .setProductId(0L)
                .build();

        doThrow(new InvalidProductRequestException(
                "Product ID must be greater than zero"
        )).when(productRequestValidator)
                .validateGetProductRequest(request);

        productGrpcService.getProduct(request, responseObserver);

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
        CreateProductRequest request = CreateProductRequest.newBuilder()
                .setName("Laptop")
                .setPrice("invalid-price")
                .setStock(10)
                .build();

        doThrow(new InvalidProductRequestException(
                "Price must be a valid monetary value"
        )).when(productRequestValidator)
                .validateCreateProductRequest(request);

        productGrpcService.createProduct(
                request,
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
        UpdateProductRequest request = UpdateProductRequest.newBuilder()
                .setProductId(0L)
                .setName("Gaming Laptop")
                .setPrice("75000.00")
                .setStock(20)
                .build();

        doThrow(new InvalidProductRequestException(
                "Product ID must be greater than zero"
        )).when(productRequestValidator)
                .validateUpdateProductRequest(request);

        productGrpcService.updateProduct(
                request,
                responseObserver
        );

        verifyErrorStatus(Status.Code.INVALID_ARGUMENT);

        verify(productService, never()).updateProduct(
                0L,
                "Gaming Laptop",
                new BigDecimal("75000.00"),
                20
        );
    }

    @Test
    void updateProductShouldReturnInvalidArgumentWhenPriceIsInvalid() {
        UpdateProductRequest request = UpdateProductRequest.newBuilder()
                .setProductId(1L)
                .setName("Gaming Laptop")
                .setPrice("invalid-price")
                .setStock(20)
                .build();

        doThrow(new InvalidProductRequestException(
                "Price must be a valid monetary value"
        )).when(productRequestValidator)
                .validateUpdateProductRequest(request);

        productGrpcService.updateProduct(
                request,
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

        when(productService.updateStock(1L, 5, "operation-1"))
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
                        .setOperationId("operation-1")
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

        verify(productService).updateStock(1L, 5, "operation-1");
        verifyNoMoreInteractions(productService);
        verify(productGrpcMapper).toProductResponse(product);
    }

    @Test
    void updateStockShouldReturnInvalidArgumentWhenProductIdIsInvalid() {
        UpdateStockRequest request = UpdateStockRequest.newBuilder()
                .setProductId(0L)
                .setQuantity(5)
                .setOperationId("operation-1")
                .build();

        doThrow(new InvalidProductRequestException(
                "Product ID must be greater than zero"
        )).when(productRequestValidator)
                .validateUpdateStockRequest(request);

        productGrpcService.updateStock(
                request,
                responseObserver
        );

        verifyErrorStatus(Status.Code.INVALID_ARGUMENT);

        verify(productService, never()).updateStock(0L, 5, "operation-1");
        verifyNoMoreInteractions(productService);
    }

    @Test
    void updateStockShouldReturnInvalidArgumentWhenQuantityIsZero() {
        UpdateStockRequest request = UpdateStockRequest.newBuilder()
                .setProductId(1L)
                .setQuantity(0)
                .setOperationId("operation-1")
                .build();

        doThrow(new InvalidProductRequestException(
                "Stock quantity cannot be zero"
        )).when(productRequestValidator)
                .validateUpdateStockRequest(request);

        productGrpcService.updateStock(request, responseObserver);

        verifyErrorStatus(Status.Code.INVALID_ARGUMENT);

        verifyNoMoreInteractions(productService);
    }

    @Test
    void updateStockShouldReturnNotFoundWhenProductDoesNotExist() {
        when(productService.updateStock(1L, 5, "operation-1"))
                .thenThrow(new ProductNotFoundException(1L));

        productGrpcService.updateStock(
                UpdateStockRequest.newBuilder()
                        .setProductId(1L)
                        .setQuantity(5)
                        .setOperationId("operation-1")
                        .build(),
                responseObserver
        );

        verifyErrorStatus(Status.Code.NOT_FOUND);

        verify(productService).updateStock(1L, 5, "operation-1");
        verifyNoMoreInteractions(productService);
    }

    @Test
    void updateStockShouldReturnFailedPreconditionWhenStockIsInsufficient() {
        when(productService.updateStock(1L, -15, "operation-1"))
                .thenThrow(new InsufficientStockException(1L, -15));

        productGrpcService.updateStock(
                UpdateStockRequest.newBuilder()
                        .setProductId(1L)
                        .setQuantity(-15)
                        .setOperationId("operation-1")
                        .build(),
                responseObserver
        );

        verifyErrorStatus(Status.Code.FAILED_PRECONDITION);

        verify(productService).updateStock(1L, -15, "operation-1");
        verifyNoMoreInteractions(productService);
    }

    @Test
    void updateStockShouldReturnAbortedWhenConcurrencyConflictOccurs() {
        when(productService.updateStock(1L, -5, "operation-1"))
                .thenThrow(new ProductConcurrencyException(1L));

        productGrpcService.updateStock(
                UpdateStockRequest.newBuilder()
                        .setProductId(1L)
                        .setQuantity(-5)
                        .setOperationId("operation-1")
                        .build(),
                responseObserver
        );

        verifyErrorStatus(Status.Code.ABORTED);

        verify(productService).updateStock(1L, -5, "operation-1");
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

    @Test
    void updateStockShouldReturnInvalidArgumentWhenOperationIdIsMissing() {
        UpdateStockRequest request = UpdateStockRequest.newBuilder()
                .setProductId(1L)
                .setQuantity(5)
                .build();

        doThrow(new InvalidProductRequestException(
                "Operation ID cannot be empty"
        )).when(productRequestValidator)
                .validateUpdateStockRequest(request);

        productGrpcService.updateStock(request, responseObserver);

        verifyErrorStatus(Status.Code.INVALID_ARGUMENT);

        verifyNoMoreInteractions(productService);
    }

    @Test
    void updateStockShouldReturnInternalWhenUnexpectedExceptionOccurs() {
        when(productService.updateStock(1L, 5, "operation-1"))
                .thenThrow(new RuntimeException("Unexpected failure"));

        productGrpcService.updateStock(
                UpdateStockRequest.newBuilder()
                        .setProductId(1L)
                        .setQuantity(5)
                        .setOperationId("operation-1")
                        .build(),
                responseObserver
        );

        verifyErrorStatus(Status.Code.INTERNAL);

        verify(productService).updateStock(
                1L,
                5,
                "operation-1"
        );
    }

    @Test
    void compensateStockShouldReturnMappedResponseWhenStockIsCompensated() {
        ProductResponseDto product = new ProductResponseDto(
                1L,
                "Laptop",
                new BigDecimal("50000.00"),
                15
        );

        ProductResponse productResponse = ProductResponse.newBuilder()
                .setProductId(1L)
                .setName("Laptop")
                .setPrice("50000.00")
                .setStock(15)
                .build();

        when(productService.compensateStock(
                1L,
                5,
                "compensation-1"
        )).thenReturn(product);

        when(productGrpcMapper.toProductResponse(product))
                .thenReturn(productResponse);

        productGrpcService.compensateStock(
                CompensateStockRequest.newBuilder()
                        .setProductId(1L)
                        .setQuantity(5)
                        .setCompensationId("compensation-1")
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

        verify(productService).compensateStock(
                1L,
                5,
                "compensation-1"
        );
        verify(productGrpcMapper).toProductResponse(product);
        verifyNoMoreInteractions(productService);
    }

    @Test
    void compensateStockShouldReturnInvalidArgumentWhenProductIdIsInvalid() {
        CompensateStockRequest request = CompensateStockRequest.newBuilder()
                .setProductId(0L)
                .setQuantity(5)
                .setCompensationId("compensation-1")
                .build();

        doThrow(new InvalidProductRequestException(
                "Product ID must be greater than zero"
        )).when(productRequestValidator)
                .validateCompensateStockRequest(request);

        productGrpcService.compensateStock(
                request,
                responseObserver
        );

        verifyErrorStatus(Status.Code.INVALID_ARGUMENT);

        verify(productService, never()).compensateStock(
                0L,
                5,
                "compensation-1"
        );

        verifyNoMoreInteractions(productService);
    }

    @Test
    void compensateStockShouldReturnInvalidArgumentWhenCompensationIdIsMissing() {
        CompensateStockRequest request = CompensateStockRequest.newBuilder()
                .setProductId(1L)
                .setQuantity(5)
                .build();

        doThrow(new InvalidProductRequestException(
                "Compensation ID cannot be empty"
        )).when(productRequestValidator)
                .validateCompensateStockRequest(request);

        productGrpcService.compensateStock(
                request,
                responseObserver
        );

        verifyErrorStatus(Status.Code.INVALID_ARGUMENT);

        verify(productService, never()).compensateStock(
                1L,
                5,
                ""
        );

        verifyNoMoreInteractions(productService);
    }

    @Test
    void compensateStockShouldReturnInvalidArgumentWhenQuantityIsZero() {
        CompensateStockRequest request = CompensateStockRequest.newBuilder()
                .setProductId(1L)
                .setQuantity(0)
                .setCompensationId("compensation-1")
                .build();

        doThrow(new InvalidProductRequestException(
                "Stock quantity cannot be zero"
        )).when(productRequestValidator)
                .validateCompensateStockRequest(request);

        productGrpcService.compensateStock(
                request,
                responseObserver
        );

        verifyErrorStatus(Status.Code.INVALID_ARGUMENT);

        verify(productService, never()).compensateStock(
                1L,
                0,
                "compensation-1"
        );

        verifyNoMoreInteractions(productService);
    }

    @Test
    void compensateStockShouldReturnNotFoundWhenProductDoesNotExist() {
        when(productService.compensateStock(
                1L,
                5,
                "compensation-1"
        )).thenThrow(new ProductNotFoundException(1L));

        productGrpcService.compensateStock(
                CompensateStockRequest.newBuilder()
                        .setProductId(1L)
                        .setQuantity(5)
                        .setCompensationId("compensation-1")
                        .build(),
                responseObserver
        );

        verifyErrorStatus(Status.Code.NOT_FOUND);

        verify(productService).compensateStock(
                1L,
                5,
                "compensation-1"
        );

        verifyNoMoreInteractions(productService);
    }

    @Test
    void compensateStockShouldReturnFailedPreconditionWhenStockIsInsufficient() {
        when(productService.compensateStock(
                1L,
                -15,
                "compensation-1"
        )).thenThrow(new InsufficientStockException(1L, -15));

        productGrpcService.compensateStock(
                CompensateStockRequest.newBuilder()
                        .setProductId(1L)
                        .setQuantity(-15)
                        .setCompensationId("compensation-1")
                        .build(),
                responseObserver
        );

        verifyErrorStatus(Status.Code.FAILED_PRECONDITION);

        verify(productService).compensateStock(
                1L,
                -15,
                "compensation-1"
        );

        verifyNoMoreInteractions(productService);
    }

    @Test
    void compensateStockShouldReturnAbortedWhenConcurrencyConflictOccurs() {
        when(productService.compensateStock(
                1L,
                5,
                "compensation-1"
        )).thenThrow(new ProductConcurrencyException(1L));

        productGrpcService.compensateStock(
                CompensateStockRequest.newBuilder()
                        .setProductId(1L)
                        .setQuantity(5)
                        .setCompensationId("compensation-1")
                        .build(),
                responseObserver
        );

        verifyErrorStatus(Status.Code.ABORTED);

        verify(productService).compensateStock(
                1L,
                5,
                "compensation-1"
        );

        verifyNoMoreInteractions(productService);
    }

    @Test
    void compensateStockShouldReturnInternalWhenUnexpectedExceptionOccurs() {
        when(productService.compensateStock(
                1L,
                5,
                "compensation-1"
        )).thenThrow(new RuntimeException("Unexpected failure"));

        productGrpcService.compensateStock(
                CompensateStockRequest.newBuilder()
                        .setProductId(1L)
                        .setQuantity(5)
                        .setCompensationId("compensation-1")
                        .build(),
                responseObserver
        );

        verifyErrorStatus(Status.Code.INTERNAL);

        verify(productService).compensateStock(
                1L,
                5,
                "compensation-1"
        );
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