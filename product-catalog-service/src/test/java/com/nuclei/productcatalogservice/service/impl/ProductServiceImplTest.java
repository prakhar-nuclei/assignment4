package com.nuclei.productcatalogservice.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import com.nuclei.productcatalogservice.entity.Product;
import com.nuclei.productcatalogservice.exception.InsufficientStockException;
import com.nuclei.productcatalogservice.exception.InvalidStockOperationException;
import com.nuclei.productcatalogservice.exception.ProductConcurrencyException;
import com.nuclei.productcatalogservice.exception.ProductNotFoundException;
import com.nuclei.productcatalogservice.repository.ProductRepository;
import com.nuclei.productcatalogservice.service.ProductStockLock;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductStockLock productStockLock;

    @Mock
    private TransactionTemplate transactionTemplate;

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

        when(productStockLock.acquire(1L)).thenReturn("lock-token");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        var response = productService.updateStock(1L, 5);

        assertEquals(15, response.stock());
        assertEquals(15, product.getStock());

        verify(productStockLock).acquire(1L);
        verify(productStockLock).release(1L, "lock-token");
        verify(productRepository).findById(1L);
        verify(productRepository).save(product);
        verifyNoMoreInteractions(productRepository);
    }

    @Test
    void updateStockShouldDecreaseStockWhenQuantityIsNegative() {
        mockSuccessfulTransaction();

        when(productStockLock.acquire(1L)).thenReturn("lock-token");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        var response = productService.updateStock(1L, -4);

        assertEquals(6, response.stock());
        assertEquals(6, product.getStock());

        verify(productStockLock).acquire(1L);
        verify(productStockLock).release(1L, "lock-token");
        verify(productRepository).findById(1L);
        verify(productRepository).save(product);
        verifyNoMoreInteractions(productRepository);
    }

    @Test
    void updateStockShouldThrowExceptionWhenQuantityIsZero() {
        mockSuccessfulTransaction();

        when(productStockLock.acquire(1L)).thenReturn("lock-token");

        assertThrows(
                InvalidStockOperationException.class,
                () -> productService.updateStock(1L, 0)
        );

        verify(productStockLock).acquire(1L);
        verify(productStockLock).release(1L, "lock-token");
        verifyNoMoreInteractions(productRepository);
    }

    @Test
    void updateStockShouldThrowExceptionWhenProductDoesNotExist() {
        mockSuccessfulTransaction();

        when(productStockLock.acquire(1L)).thenReturn("lock-token");
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(
                ProductNotFoundException.class,
                () -> productService.updateStock(1L, 5)
        );

        verify(productStockLock).acquire(1L);
        verify(productStockLock).release(1L, "lock-token");
        verify(productRepository).findById(1L);
        verifyNoMoreInteractions(productRepository);
    }

    @Test
    void updateStockShouldThrowExceptionWhenStockIsInsufficient() {
        mockSuccessfulTransaction();

        when(productStockLock.acquire(1L)).thenReturn("lock-token");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThrows(
                InsufficientStockException.class,
                () -> productService.updateStock(1L, -11)
        );

        assertEquals(10, product.getStock());

        verify(productStockLock).acquire(1L);
        verify(productStockLock).release(1L, "lock-token");
        verify(productRepository).findById(1L);
        verifyNoMoreInteractions(productRepository);
    }

    @Test
    void updateStockShouldReturnUpdatedProductWhenStockIsUpdated() {
        mockSuccessfulTransaction();

        when(productStockLock.acquire(1L)).thenReturn("lock-token");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        var response = productService.updateStock(1L, 5);

        assertEquals(product.getId(), response.productId());
        assertEquals(product.getName(), response.name());
        assertEquals(product.getPrice(), response.price());
        assertEquals(15, response.stock());

        verify(productStockLock).acquire(1L);
        verify(productStockLock).release(1L, "lock-token");
        verify(productRepository).findById(1L);
        verify(productRepository).save(product);
        verifyNoMoreInteractions(productRepository);
    }

    @Test
    void updateStockShouldThrowConcurrencyExceptionWhenLockCannotBeAcquired() {
        when(productStockLock.acquire(1L))
                .thenThrow(new ProductConcurrencyException(1L));

        assertThrows(
                ProductConcurrencyException.class,
                () -> productService.updateStock(1L, 5)
        );

        verify(productStockLock).acquire(1L);
        verifyNoMoreInteractions(productRepository);
    }

    @Test
    void updateStockShouldReleaseLockWhenTransactionFails() {
        mockSuccessfulTransaction();

        when(productStockLock.acquire(1L)).thenReturn("lock-token");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(product))
                .thenThrow(new RuntimeException("Database failure"));

        assertThrows(
                RuntimeException.class,
                () -> productService.updateStock(1L, 5)
        );

        verify(productStockLock).acquire(1L);
        verify(productStockLock).release(1L, "lock-token");
        verify(productRepository).findById(1L);
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
}