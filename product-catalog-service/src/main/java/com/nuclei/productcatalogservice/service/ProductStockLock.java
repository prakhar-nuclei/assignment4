package com.nuclei.productcatalogservice.service;

public interface ProductStockLock {

    String acquire(Long productId);

    void release(Long productId, String lockToken);
}
