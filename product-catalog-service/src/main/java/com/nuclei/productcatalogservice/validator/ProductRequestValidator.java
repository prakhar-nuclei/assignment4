package com.nuclei.productcatalogservice.validator;

import com.nuclei.product.proto.CompensateStockRequest;
import com.nuclei.product.proto.CreateProductRequest;
import com.nuclei.product.proto.GetProductRequest;
import com.nuclei.product.proto.StockOperationDirection;
import com.nuclei.product.proto.UpdateProductRequest;
import com.nuclei.product.proto.UpdateStockRequest;
import com.nuclei.productcatalogservice.exception.InvalidProductRequestException;
import java.math.BigDecimal;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@NoArgsConstructor
public class ProductRequestValidator {

    private static final String INVALID_PRODUCT_ID_MESSAGE =
            "Product ID must be greater than zero";

    public void validateGetProductRequest(final GetProductRequest request) {
        validateProductId(request.getProductId());
    }

    public void validateUpdateStockRequest(final UpdateStockRequest request) {
        validateProductId(request.getProductId());

        if (request.getQuantity() <= 0) {
            throw new InvalidProductRequestException(
                    "Stock quantity must be greater than zero"
            );
        }

        if (request.getOperationId().isBlank()) {
            throw new InvalidProductRequestException(
                    "Operation ID cannot be empty"
            );
        }

        validateStockDirection(request.getDirection());
    }

    public void validateCompensateStockRequest(
            final CompensateStockRequest request) {

        validateProductId(request.getProductId());

        if (request.getQuantity() <= 0) {
            throw new InvalidProductRequestException(
                    "Stock quantity must be greater than zero"
            );
        }

        if (request.getCompensationId().isBlank()) {
            throw new InvalidProductRequestException(
                    "Compensation ID cannot be empty"
            );
        }

        validateStockDirection(request.getDirection());
    }

    public void validateCreateProductRequest(
            final CreateProductRequest request) {

        if (request.getName().isBlank()) {
            throw new InvalidProductRequestException(
                    "Product name cannot be empty"
            );
        }

        if (request.getPrice().isBlank()) {
            throw new InvalidProductRequestException(
                    "Product price cannot be empty"
            );
        }

        try {
            new BigDecimal(request.getPrice());
        } catch (NumberFormatException exception) {
            throw new InvalidProductRequestException(
                    "Price must be a valid monetary value",
                    exception
            );
        }

        if (request.getStock() < 0) {
            throw new InvalidProductRequestException(
                    "Stock cannot be negative"
            );
        }
    }

    public void validateUpdateProductRequest(
            final UpdateProductRequest request) {

        validateProductId(request.getProductId());

        if (request.getName().isBlank()) {
            throw new InvalidProductRequestException(
                    "Product name cannot be empty"
            );
        }

        if (request.getPrice().isBlank()) {
            throw new InvalidProductRequestException(
                    "Product price cannot be empty"
            );
        }

        try {
            new BigDecimal(request.getPrice());
        } catch (NumberFormatException exception) {
            throw new InvalidProductRequestException(
                    "Price must be a valid monetary value",
                    exception
            );
        }

        if (request.getStock() < 0) {
            throw new InvalidProductRequestException(
                    "Stock cannot be negative"
            );
        }
    }

    private void validateStockDirection(
            final StockOperationDirection direction) {

        if (direction == StockOperationDirection.STOCK_OPERATION_DIRECTION_UNSPECIFIED
                || direction == StockOperationDirection.UNRECOGNIZED) {
            throw new InvalidProductRequestException(
                    "Stock operation direction must be specified"
            );
        }
    }

    private void validateProductId(final long productId) {
        if (productId <= 0) {
            throw new InvalidProductRequestException(INVALID_PRODUCT_ID_MESSAGE);
        }
    }
}