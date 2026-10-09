package com.nuclei.productcatalogservice.service.impl;

import com.nuclei.productcatalogservice.dto.ProductResponseDto;
import com.nuclei.productcatalogservice.entity.Product;
import com.nuclei.productcatalogservice.entity.StockOperation;
import com.nuclei.productcatalogservice.enums.EntityStatusEnum;
import com.nuclei.productcatalogservice.enums.StockOperationTypeEnum;
import com.nuclei.productcatalogservice.exception.InsufficientStockException;
import com.nuclei.productcatalogservice.exception.InvalidStockOperationException;
import com.nuclei.productcatalogservice.exception.ProductConcurrencyException;
import com.nuclei.productcatalogservice.exception.ProductNotFoundException;
import com.nuclei.productcatalogservice.mapper.ProductMapper;
import com.nuclei.productcatalogservice.repository.ProductRepository;
import com.nuclei.productcatalogservice.repository.StockOperationRepository;
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
    private final StockOperationRepository stockOperationRepository;
    private final ProductMapper productMapper;

    public ProductServiceImpl(
            final ProductRepository productRepository,
            final TransactionTemplate transactionTemplate,
            final StockOperationRepository stockOperationRepository,
            final ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.transactionTemplate = transactionTemplate;
        this.stockOperationRepository = stockOperationRepository;
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
            final Integer quantity,
            final String operationId) {

        return executeWithLockRetry(
                () -> transactionTemplate.execute(status ->
                        updateStockInTransaction(productId, quantity,operationId)),
                productId
        );
    }

    @Override
    public ProductResponseDto compensateStock(
            final Long productId,
            final Integer quantity,
            final String compensationId) {

        return executeWithLockRetry(
                () -> transactionTemplate.execute(status ->
                        compensateStockInTransaction(
                                productId,
                                quantity,
                                compensationId)),
                productId
        );
    }

    private ProductResponseDto compensateStockInTransaction(
            final Long productId,
            final Integer quantity,
            final String compensationId) {

        if (quantity == 0) {
            throw new InvalidStockOperationException();
        }

        final Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        final StockOperation existingOperation =
                stockOperationRepository.findByOperationId(compensationId)
                        .orElse(null);

        final ProductResponseDto response;

        if (existingOperation != null) {
            response = productMapper.toResponseDto(product);
        } else {
            final int updatedStock = product.getStock() + quantity;
            if (updatedStock < 0) {
                throw new InsufficientStockException(productId, quantity);
            }

            product.setStock(updatedStock);
            final Product savedProduct = productRepository.save(product);

            saveStockOperation(
                    compensationId,
                    productId,
                    quantity,
                    StockOperationTypeEnum.COMPENSATION);

            response = productMapper.toResponseDto(savedProduct);
        }

        return response;
    }

    private ProductResponseDto updateStockInTransaction(
            final Long productId,
            final Integer quantity,
            final String operationId) {

        if (quantity == 0) {
            throw new InvalidStockOperationException();
        }

        final Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        final StockOperation existingOperation =
                stockOperationRepository.findByOperationId(operationId)
                        .orElse(null);

        final ProductResponseDto response;

        if (existingOperation != null) {
            response = productMapper.toResponseDto(product);
        } else {
            final int updatedStock = product.getStock() + quantity;
            if (updatedStock < 0) {
                throw new InsufficientStockException(productId, quantity);
            }

            product.setStock(updatedStock);
            final Product savedProduct = productRepository.save(product);

            saveStockOperation(
                    operationId,
                    productId,
                    quantity,
                    StockOperationTypeEnum.STOCK_UPDATE);

            response = productMapper.toResponseDto(savedProduct);
        }

        return response;
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

    private void saveStockOperation(
            final String operationId,
            final Long productId,
            final Integer quantity,
            final StockOperationTypeEnum operationType) {

        final StockOperation stockOperation = new StockOperation();
        stockOperation.setOperationId(operationId);
        stockOperation.setProductId(productId);
        stockOperation.setQuantity(quantity);
        stockOperation.setOperationType(operationType);

        stockOperationRepository.save(stockOperation);
    }
}