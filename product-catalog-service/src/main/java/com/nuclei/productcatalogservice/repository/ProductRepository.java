package com.nuclei.productcatalogservice.repository;

import com.nuclei.productcatalogservice.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}