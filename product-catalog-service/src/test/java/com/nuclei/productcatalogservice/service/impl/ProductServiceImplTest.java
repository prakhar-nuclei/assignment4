package com.nuclei.productcatalogservice.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import com.nuclei.productcatalogservice.dto.ProductResponseDto;
import com.nuclei.productcatalogservice.entity.Product;
import com.nuclei.productcatalogservice.enums.EntityStatusEnum;
import com.nuclei.productcatalogservice.exception.InsufficientStockException;
import com.nuclei.productcatalogservice.exception.InvalidStockOperationException;
import com.nuclei.productcatalogservice.exception.ProductNotFoundException;
import com.nuclei.productcatalogservice.mapper.ProductMapper;
import com.nuclei.productcatalogservice.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private ProductServiceImpl productService;

    private Product product;

    @BeforeEach
    void setUp() {
        product = new Product();
        product.setId(1L);
        product.setName("Laptop");
        product.setPrice(new BigDecimal("50000.00"));
        product.setStock(10);
    }

    @Test
    void getProductShouldReturnProductWhenProductExists() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        when(productMapper.toResponseDto(product))
                .thenReturn(new ProductResponseDto(
                        1L,
                        "Laptop",
                        new BigDecimal("50000.00"),
                        10
                ));

        var response = productService.getProduct(1L);

        assertEquals(1L, response.productId());
        assertEquals("Laptop", response.name());
        assertEquals(new BigDecimal("50000.00"), response.price());
        assertEquals(10, response.stock());

        verify(productRepository).findById(1L);
        verifyNoMoreInteractions(productRepository);
    }

    @Test
    void getProductShouldThrowExceptionWhenProductDoesNotExist() {
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(
                ProductNotFoundException.class,
                () -> productService.getProduct(1L)
        );

        verify(productRepository).findById(1L);
        verifyNoMoreInteractions(productRepository);
    }

    @Test
    void updateStockShouldIncreaseStockWhenQuantityIsPositive() {
        mockSuccessfulTransaction();

        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        when(productMapper.toResponseDto(product))
                .thenReturn(new ProductResponseDto(
                        1L,
                        "Laptop",
                        new BigDecimal("50000.00"),
                        15
                ));

        var response = productService.updateStock(1L, 5);

        assertEquals(15, response.stock());
        assertEquals(15, product.getStock());

        verify(productRepository).findByIdForUpdate(1L);
        verify(productRepository).save(product);
        verifyNoMoreInteractions(productRepository);
    }

    @Test
    void updateStockShouldDecreaseStockWhenQuantityIsNegative() {
        mockSuccessfulTransaction();

        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        when(productMapper.toResponseDto(product))
                .thenReturn(new ProductResponseDto(
                        1L,
                        "Laptop",
                        new BigDecimal("50000.00"),
                        6
                ));

        var response = productService.updateStock(1L, -4);

        assertEquals(6, response.stock());
        assertEquals(6, product.getStock());

        verify(productRepository).findByIdForUpdate(1L);
        verify(productRepository).save(product);
        verifyNoMoreInteractions(productRepository);
    }

    @Test
    void updateStockShouldThrowExceptionWhenQuantityIsZero() {
        mockSuccessfulTransaction();

        assertThrows(
                InvalidStockOperationException.class,
                () -> productService.updateStock(1L, 0)
        );

        verifyNoMoreInteractions(productRepository);
    }

    @Test
    void updateStockShouldThrowExceptionWhenProductDoesNotExist() {
        mockSuccessfulTransaction();


        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        assertThrows(
                ProductNotFoundException.class,
                () -> productService.updateStock(1L, 5)
        );

        verify(productRepository).findByIdForUpdate(1L);
        verifyNoMoreInteractions(productRepository);
    }

    @Test
    void updateStockShouldThrowExceptionWhenStockIsInsufficient() {
        mockSuccessfulTransaction();

        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));

        assertThrows(
                InsufficientStockException.class,
                () -> productService.updateStock(1L, -11)
        );

        assertEquals(10, product.getStock());

        verify(productRepository).findByIdForUpdate(1L);
        verifyNoMoreInteractions(productRepository);
    }

    @Test
    void updateStockShouldReturnUpdatedProductWhenStockIsUpdated() {
        mockSuccessfulTransaction();

        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        when(productMapper.toResponseDto(product))
                .thenReturn(new ProductResponseDto(
                        1L,
                        "Laptop",
                        new BigDecimal("50000.00"),
                        15
                ));

        var response = productService.updateStock(1L, 5);

        assertEquals(product.getId(), response.productId());
        assertEquals(product.getName(), response.name());
        assertEquals(product.getPrice(), response.price());
        assertEquals(15, response.stock());

        verify(productRepository).findByIdForUpdate(1L);
        verify(productRepository).save(product);
        verifyNoMoreInteractions(productRepository);
    }

    @Test
    void updateStockShouldRetryWhenPessimisticLockFails() {
        mockSuccessfulTransaction();

        when(productRepository.findByIdForUpdate(1L))
                .thenThrow(new PessimisticLockingFailureException("Lock failed"))
                .thenReturn(Optional.of(product));

        when(productRepository.save(product)).thenReturn(product);

        when(productMapper.toResponseDto(product))
                .thenReturn(new ProductResponseDto(
                        1L,
                        "Laptop",
                        new BigDecimal("50000.00"),
                        15
                ));

        var response = productService.updateStock(1L, 5);

        assertEquals(15, response.stock());

        verify(productRepository, org.mockito.Mockito.times(2))
                .findByIdForUpdate(1L);
        verify(productRepository).save(product);
    }

    @Test
    void updateStockShouldPropagateTransactionFailure() {
        mockSuccessfulTransaction();

        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(product))
                .thenThrow(new RuntimeException("Database failure"));

        assertThrows(
                RuntimeException.class,
                () -> productService.updateStock(1L, 5)
        );

        verify(productRepository).findByIdForUpdate(1L);
        verify(productRepository).save(product);
        verifyNoMoreInteractions(productRepository);
    }

    @Test
    void createProductShouldCreateAndReturnProduct() {
        mockSuccessfulTransaction();

        ProductResponseDto response = new ProductResponseDto(
                1L,
                "Laptop",
                new BigDecimal("50000.00"),
                10
        );

        when(productRepository.save(any(Product.class))).thenReturn(product);
        when(productMapper.toResponseDto(product)).thenReturn(response);

        var result = productService.createProduct(
                "Laptop",
                new BigDecimal("50000.00"),
                10
        );

        assertEquals(1L, result.productId());
        assertEquals("Laptop", result.name());
        assertEquals(new BigDecimal("50000.00"), result.price());
        assertEquals(10, result.stock());

        verify(productRepository).save(any(Product.class));
        verify(productMapper).toResponseDto(product);
        verifyNoMoreInteractions(productRepository);
    }

    @Test
    void createProductShouldPropagateExceptionWhenSaveFails() {
        mockSuccessfulTransaction();

        when(productRepository.save(any(Product.class)))
                .thenThrow(new RuntimeException("Database failure"));

        assertThrows(
                RuntimeException.class,
                () -> productService.createProduct(
                        "Laptop",
                        new BigDecimal("50000.00"),
                        10
                )
        );

        verify(productRepository).save(any(Product.class));
        verifyNoMoreInteractions(productRepository);
    }

    @Test
    void updateProductShouldUpdateAndReturnProduct() {
        mockSuccessfulTransaction();

        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        ProductResponseDto response = new ProductResponseDto(
                1L,
                "Gaming Laptop",
                new BigDecimal("75000.00"),
                20
        );

        when(productMapper.toResponseDto(product)).thenReturn(response);

        var result = productService.updateProduct(
                1L,
                "Gaming Laptop",
                new BigDecimal("75000.00"),
                20
        );

        assertEquals(1L, result.productId());
        assertEquals("Gaming Laptop", result.name());
        assertEquals(new BigDecimal("75000.00"), result.price());
        assertEquals(20, result.stock());

        assertEquals("Gaming Laptop", product.getName());
        assertEquals(new BigDecimal("75000.00"), product.getPrice());
        assertEquals(20, product.getStock());

        verify(productRepository).findByIdForUpdate(1L);
        verify(productRepository).save(product);
        verify(productMapper).toResponseDto(product);
        verifyNoMoreInteractions(productRepository);
    }

    @Test
    void updateProductShouldThrowExceptionWhenProductDoesNotExist() {
        mockSuccessfulTransaction();

        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        assertThrows(
                ProductNotFoundException.class,
                () -> productService.updateProduct(
                        1L,
                        "Gaming Laptop",
                        new BigDecimal("75000.00"),
                        20
                )
        );

        verify(productRepository).findByIdForUpdate(1L);
        verifyNoMoreInteractions(productRepository);
    }

    @Test
    void deleteProductShouldMarkProductAsInactive() {
        mockSuccessfulTransactionWithoutResult();

        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        productService.deleteProduct(1L);

        assertEquals(EntityStatusEnum.INACTIVE, product.getStatus());

        verify(productRepository).findByIdForUpdate(1L);
        verify(productRepository).save(product);
        verifyNoMoreInteractions(productRepository);
    }

    @Test
    void deleteProductShouldThrowExceptionWhenProductDoesNotExist() {
        mockSuccessfulTransactionWithoutResult();

        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        assertThrows(
                ProductNotFoundException.class,
                () -> productService.deleteProduct(1L)
        );

        verify(productRepository).findByIdForUpdate(1L);
        verifyNoMoreInteractions(productRepository);
    }

    @Test
    void deleteProductShouldPropagateTransactionFailure() {
        mockSuccessfulTransactionWithoutResult();

        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(product))
                .thenThrow(new RuntimeException("Database failure"));

        assertThrows(
                RuntimeException.class,
                () -> productService.deleteProduct(1L)
        );

        verify(productRepository).findByIdForUpdate(1L);
        verify(productRepository).save(product);
        verifyNoMoreInteractions(productRepository);
    }

    private void mockSuccessfulTransaction() {
        when(transactionTemplate.execute(any(TransactionCallback.class)))
                .thenAnswer(invocation -> {
                    TransactionCallback<?> callback = invocation.getArgument(0);
                    return callback.doInTransaction(null);
                });
    }

    private void mockSuccessfulTransactionWithoutResult() {
        doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }
}