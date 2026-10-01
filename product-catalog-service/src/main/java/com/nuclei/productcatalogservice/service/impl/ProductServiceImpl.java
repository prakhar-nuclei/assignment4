package com.nuclei.productcatalogservice.service.impl;

import com.nuclei.productcatalogservice.dto.ProductResponseDto;
import com.nuclei.productcatalogservice.entity.Product;
import com.nuclei.productcatalogservice.enums.EntityStatusEnum;
import com.nuclei.productcatalogservice.exception.InsufficientStockException;
import com.nuclei.productcatalogservice.exception.InvalidStockOperationException;
import com.nuclei.productcatalogservice.exception.ProductConcurrencyException;
import com.nuclei.productcatalogservice.exception.ProductNotFoundException;
import com.nuclei.productcatalogservice.mapper.ProductMapper;
import com.nuclei.productcatalogservice.repository.ProductRepository;
import com.nuclei.productcatalogservice.service.ProductService;
import java.math.BigDecimal;
import java.util.function.Supplier;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ProductServiceImpl implements ProductService {

    private static final int MAX_LOCK_RETRIES = 3;

    private final ProductRepository productRepository;
    private final TransactionTemplate transactionTemplate;
    private final ProductMapper productMapper;

    public ProductServiceImpl(
            final ProductRepository productRepository,
            final TransactionTemplate transactionTemplate,
            final ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.transactionTemplate = transactionTemplate;
        this.productMapper = productMapper;
    }

    @Override
    public ProductResponseDto getProduct(final Long productId) {
        final Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        return productMapper.toResponseDto(product);
    }

    @Override
    public ProductResponseDto updateStock(
            final Long productId,
            final Integer quantity) {

        return executeWithLockRetry(
                () -> transactionTemplate.execute(status ->
                        updateStockInTransaction(productId, quantity)),
                productId
        );
    }

    private ProductResponseDto updateStockInTransaction(
            final Long productId,
            final Integer quantity) {

        if (quantity == 0) {
            throw new InvalidStockOperationException();
        }

        final Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        final int updatedStock = product.getStock() + quantity;

        if (updatedStock < 0) {
            throw new InsufficientStockException(productId, quantity);
        }

        product.setStock(updatedStock);

        final Product savedProduct = productRepository.save(product);

        return productMapper.toResponseDto(savedProduct);
    }

    @Override
    public ProductResponseDto createProduct(
            final String name,
            final BigDecimal price,
            final Integer stock) {

        return transactionTemplate.execute(status -> {
            final Product product = new Product();
            product.setName(name);
            product.setPrice(price);
            product.setStock(stock);

            final Product savedProduct = productRepository.save(product);

            return productMapper.toResponseDto(savedProduct);
        });
    }

    @Override
    public ProductResponseDto updateProduct(
            final Long productId,
            final String name,
            final BigDecimal price,
            final Integer stock) {

        return executeWithLockRetry(
                () -> transactionTemplate.execute(status -> {
                    final Product product = productRepository.findByIdForUpdate(productId)
                            .orElseThrow(() -> new ProductNotFoundException(productId));

                    product.setName(name);
                    product.setPrice(price);
                    product.setStock(stock);

                    final Product savedProduct = productRepository.save(product);

                    return productMapper.toResponseDto(savedProduct);
                }),
                productId
        );
    }

    @Override
    public void deleteProduct(final Long productId) {
        executeWithLockRetry(
                () -> {
                    transactionTemplate.executeWithoutResult(status -> {
                        final Product product = productRepository.findByIdForUpdate(productId)
                                .orElseThrow(() -> new ProductNotFoundException(productId));

                        product.setStatus(EntityStatusEnum.INACTIVE);
                        productRepository.save(product);
                    });

                    return null;
                },
                productId
        );
    }

    private <T> T executeWithLockRetry(
            final Supplier<T> transactionOperation,
            final Long productId) {

        for (int attempt = 1; attempt <= MAX_LOCK_RETRIES; attempt++) {
            try {
                return transactionOperation.get();
            } catch (PessimisticLockingFailureException exception) {
                if (attempt == MAX_LOCK_RETRIES) {
                    throw new ProductConcurrencyException(productId,exception);
                }
            }
        }

        throw new ProductConcurrencyException(productId);
    }
}