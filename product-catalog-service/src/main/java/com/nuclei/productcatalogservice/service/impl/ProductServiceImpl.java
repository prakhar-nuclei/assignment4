package com.nuclei.productcatalogservice.service.impl;

import com.nuclei.productcatalogservice.dto.ProductResponseDto;
import com.nuclei.productcatalogservice.entity.Product;
import com.nuclei.productcatalogservice.exception.InsufficientStockException;
import com.nuclei.productcatalogservice.exception.InvalidStockOperationException;
import com.nuclei.productcatalogservice.exception.ProductNotFoundException;
import com.nuclei.productcatalogservice.repository.ProductRepository;
import com.nuclei.productcatalogservice.service.ProductService;
import com.nuclei.productcatalogservice.service.ProductStockLock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductStockLock productStockLock;
    private final TransactionTemplate transactionTemplate;

    public ProductServiceImpl(
            ProductRepository productRepository,
            ProductStockLock productStockLock,
            TransactionTemplate transactionTemplate) {
        this.productRepository = productRepository;
        this.productStockLock = productStockLock;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public ProductResponseDto getProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        return toResponseDto(product);
    }

    @Override
    public ProductResponseDto updateStock(Long productId, Integer quantity) {
        String lockToken = productStockLock.acquire(productId);

        try {
            return transactionTemplate.execute(status ->
                    updateStockInTransaction(productId, quantity));
        } finally {
            productStockLock.release(productId, lockToken);
        }
    }

    private ProductResponseDto updateStockInTransaction(
            Long productId,
            Integer quantity) {

        if (quantity == 0) {
            throw new InvalidStockOperationException();
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        int updatedStock = product.getStock() + quantity;

        if (updatedStock < 0) {
            throw new InsufficientStockException(productId, quantity);
        }

        product.setStock(updatedStock);

        Product savedProduct = productRepository.save(product);

        return toResponseDto(savedProduct);
    }

    private ProductResponseDto toResponseDto(Product product) {
        return new ProductResponseDto(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getStock()
        );
    }
}