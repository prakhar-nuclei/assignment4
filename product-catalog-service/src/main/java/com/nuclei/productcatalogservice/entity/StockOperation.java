package com.nuclei.productcatalogservice.entity;

import com.nuclei.productcatalogservice.enums.StockOperationDirectionEnum;
import com.nuclei.productcatalogservice.enums.StockOperationTypeEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "stock_operations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StockOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operation_id", nullable = false, unique = true)
    private String operationId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false)
    private StockOperationTypeEnum operationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false)
    private StockOperationDirectionEnum direction;
}