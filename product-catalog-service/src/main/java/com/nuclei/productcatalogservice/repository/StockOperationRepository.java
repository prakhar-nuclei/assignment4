package com.nuclei.productcatalogservice.repository;

import com.nuclei.productcatalogservice.entity.StockOperation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockOperationRepository
        extends JpaRepository<StockOperation, Long> {

    Optional<StockOperation> findByOperationId(String operationId);
}