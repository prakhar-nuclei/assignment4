package com.nuclei.productcatalogservice.service.impl;

import com.nuclei.productcatalogservice.dto.ProductResponseDto;
import com.nuclei.productcatalogservice.entity.Product;
import com.nuclei.productcatalogservice.enums.EntityStatusEnum;
import com.nuclei.productcatalogservice.exception.InsufficientStockException;
import com.nuclei.productcatalogservice.exception.InvalidStockOperationException;
import com.nuclei.productcatalogservice.exception.ProductNotFoundException;
import com.nuclei.productcatalogservice.mapper.ProductMapper;
import com.nuclei.productcatalogservice.repository.ProductRepository;
import com.nuclei.productcatalogservice.service.ProductService;
import com.nuclei.productcatalogservice.service.ProductStockLock;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductStockLock productStockLock;
    private final TransactionTemplate transactionTemplate;
    private final ProductMapper productMapper;

    public ProductServiceImpl(
           final ProductRepository productRepository,
           final ProductStockLock productStockLock,
           final TransactionTemplate transactionTemplate,
           final ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.productStockLock = productStockLock;
        this.transactionTemplate = transactionTemplate;
        this.productMapper = productMapper;
    }

    @Override
    public ProductResponseDto getProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        return productMapper.toResponseDto(product);
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

        return productMapper.toResponseDto(savedProduct);
    }

    @Override
    public ProductResponseDto createProduct(
            String name,
            BigDecimal price,
            Integer stock) {

        return transactionTemplate.execute(status -> {
            Product product = new Product();
            product.setName(name);
            product.setPrice(price);
            product.setStock(stock);

            Product savedProduct = productRepository.save(product);

            return productMapper.toResponseDto(savedProduct);
        });
    }

    @Override
    public ProductResponseDto updateProduct(
            Long productId,
            String name,
            BigDecimal price,
            Integer stock) {

        String lockToken = productStockLock.acquire(productId);

        try {
            return transactionTemplate.execute(status -> {
                Product product = productRepository.findById(productId)
                        .orElseThrow(() -> new ProductNotFoundException(productId));

                product.setName(name);
                product.setPrice(price);
                product.setStock(stock);

                Product savedProduct = productRepository.save(product);

                return productMapper.toResponseDto(savedProduct);
            });
        } finally {
            productStockLock.release(productId, lockToken);
        }
    }

    @Override
    public void deleteProduct(Long productId) {
        String lockToken = productStockLock.acquire(productId);

        try {
            transactionTemplate.executeWithoutResult(status -> {
                Product product = productRepository.findById(productId)
                        .orElseThrow(() -> new ProductNotFoundException(productId));

                product.setStatus(EntityStatusEnum.INACTIVE);
                productRepository.save(product);
            });
        } finally {
            productStockLock.release(productId, lockToken);
        }
    }
}