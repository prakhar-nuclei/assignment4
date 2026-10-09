package com.nuclei.productcatalogservice.service.impl;

import com.nuclei.productcatalogservice.dto.ProductResponseDto;
import com.nuclei.productcatalogservice.entity.Product;
import com.nuclei.productcatalogservice.entity.StockOperation;
import com.nuclei.productcatalogservice.enums.EntityStatusEnum;
import com.nuclei.productcatalogservice.enums.StockOperationDirectionEnum;
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
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@SuppressWarnings("PMD.TooManyMethods")
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
            final String operationId,
            final StockOperationDirectionEnum direction) {

        validateStockOperation(quantity, operationId, direction);

        return executeWithLockRetry(
                () -> transactionTemplate.execute(status ->
                        updateStockInTransaction(
                                productId,
                                quantity,
                                operationId,
                                direction)),
                productId
        );
    }

    @Override
    public ProductResponseDto compensateStock(
            final Long productId,
            final Integer quantity,
            final String compensationId,
            final StockOperationDirectionEnum direction) {

        validateStockOperation(quantity, compensationId, direction);

        return executeWithLockRetry(
                () -> transactionTemplate.execute(status ->
                        compensateStockInTransaction(
                                productId,
                                quantity,
                                compensationId,
                                direction)),
                productId
        );
    }

    private ProductResponseDto updateStockInTransaction(
            final Long productId,
            final Integer quantity,
            final String operationId,
            final StockOperationDirectionEnum direction) {

        final Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        final StockOperation existingOperation =
                stockOperationRepository.findByOperationId(operationId)
                        .orElse(null);

        final ProductResponseDto response;

        if (existingOperation != null) {
            validateExistingOperation(
                    existingOperation,
                    productId,
                    quantity,
                    direction,
                    StockOperationTypeEnum.STOCK_UPDATE);

           response = productMapper.toResponseDto(product);
        } else {

            applyStockChange(product, productId, quantity, direction);

            final Product savedProduct = productRepository.save(product);

            saveStockOperation(
                    operationId,
                    productId,
                    quantity,
                    StockOperationTypeEnum.STOCK_UPDATE,
                    direction);

            response = productMapper.toResponseDto(savedProduct);
        }

        return response;
    }

    private ProductResponseDto compensateStockInTransaction(
            final Long productId,
            final Integer quantity,
            final String compensationId,
            final StockOperationDirectionEnum direction) {

        final Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        final StockOperation existingOperation =
                stockOperationRepository.findByOperationId(compensationId)
                        .orElse(null);

        final ProductResponseDto response;

        if (existingOperation != null) {
            validateExistingOperation(
                    existingOperation,
                    productId,
                    quantity,
                    direction,
                    StockOperationTypeEnum.COMPENSATION);

            response = productMapper.toResponseDto(product);
        } else {

            applyStockChange(product, productId, quantity, direction);

            final Product savedProduct = productRepository.save(product);

            saveStockOperation(
                    compensationId,
                    productId,
                    quantity,
                    StockOperationTypeEnum.COMPENSATION,
                    direction);

            response = productMapper.toResponseDto(savedProduct);
        }
        return response;
    }

    private void validateStockOperation(
            final Integer quantity,
            final String operationId,
            final StockOperationDirectionEnum direction) {

        if (quantity == null
                || quantity <= 0
                || operationId == null
                || operationId.isBlank()
                || direction == null) {
            throw new InvalidStockOperationException();
        }
    }

    @SuppressWarnings("PMD.LawOfDemeter")
    private void validateExistingOperation(
            final StockOperation existingOperation,
            final Long productId,
            final Integer quantity,
            final StockOperationDirectionEnum direction,
            final StockOperationTypeEnum operationType) {

        final boolean sameRequest =
                Objects.equals(existingOperation.getProductId(), productId)
                        && Objects.equals(existingOperation.getQuantity(), quantity)
                        && existingOperation.getOperationType() == operationType
                        && existingOperation.getDirection() == direction;

        if (!sameRequest) {
            throw new InvalidStockOperationException();
        }
    }

    private void applyStockChange(
            final Product product,
            final Long productId,
            final Integer quantity,
            final StockOperationDirectionEnum direction) {

        final int currentStock = product.getStock();
        final int updatedStock;

        if (direction == StockOperationDirectionEnum.INCREASE) {
            try {
                updatedStock = Math.addExact(currentStock, quantity);
            } catch (ArithmeticException exception) {
                throw new InvalidStockOperationException(exception);
            }
        } else if (direction == StockOperationDirectionEnum.DECREASE) {
            updatedStock = currentStock - quantity;

            if (updatedStock < 0) {
                throw new InsufficientStockException(productId, quantity);
            }
        } else {
            throw new InvalidStockOperationException();
        }

        product.setStock(updatedStock);
    }

    private void saveStockOperation(
            final String operationId,
            final Long productId,
            final Integer quantity,
            final StockOperationTypeEnum operationType,
            final StockOperationDirectionEnum direction) {

        final StockOperation stockOperation = new StockOperation();
        stockOperation.setOperationId(operationId);
        stockOperation.setProductId(productId);
        stockOperation.setQuantity(quantity);
        stockOperation.setOperationType(operationType);
        stockOperation.setDirection(direction);

        stockOperationRepository.save(stockOperation);
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
                    throw new ProductConcurrencyException(productId, exception);
                }
            }
        }

        throw new ProductConcurrencyException(productId);
    }
}