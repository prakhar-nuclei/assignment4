package com.nuclei.orderservice.grpc.client;

import com.nuclei.orderservice.dtos.ProductResponseDto;
import com.nuclei.orderservice.exception.InsufficientStockException;
import com.nuclei.orderservice.exception.ProductConcurrencyException;
import com.nuclei.orderservice.exception.ProductNotFoundException;
import com.nuclei.orderservice.mapper.ProductGrpcMapper;
import com.nuclei.product.proto.CompensateStockRequest;
import com.nuclei.product.proto.GetProductRequest;
import com.nuclei.product.proto.ProductResponse;
import com.nuclei.product.proto.ProductServiceGrpc;
import com.nuclei.product.proto.UpdateStockRequest;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.stereotype.Component;

@Component
public class ProductGrpcClient {

    private final ProductServiceGrpc.ProductServiceBlockingStub productServiceBlockingStub;
    private final ProductGrpcMapper productGrpcMapper;

    public ProductGrpcClient(
            final ProductServiceGrpc.ProductServiceBlockingStub productServiceBlockingStub,
            final ProductGrpcMapper productGrpcMapper) {
        this.productServiceBlockingStub = productServiceBlockingStub;
        this.productGrpcMapper = productGrpcMapper;
    }

    public ProductResponseDto getProduct(final Long productId) {
        final GetProductRequest request = GetProductRequest.newBuilder()
                .setProductId(productId)
                .build();

        try {
            final ProductResponse response =
                    productServiceBlockingStub.getProduct(request);

            return productGrpcMapper.toProductResponseDto(response);
        } catch (final StatusRuntimeException exception) {
              if (getStatusCode(exception) == Status.Code.NOT_FOUND) {
                throw new ProductNotFoundException(productId, exception);
            }

               throw exception;
        }
    }

    public ProductResponseDto updateStock(
            final Long productId,
            final Integer quantity,
            final String operationId) {

        final UpdateStockRequest request = UpdateStockRequest.newBuilder()
                .setProductId(productId)
                .setQuantity(quantity)
                .setOperationId(operationId)
                .build();

        try {
            final ProductResponse response =
                    productServiceBlockingStub.updateStock(request);

            return productGrpcMapper.toProductResponseDto(response);
        } catch (final StatusRuntimeException exception) {
            throw mapStockOperationException(exception, productId);
        }
    }

    public ProductResponseDto compensateStock(
            final Long productId,
            final Integer quantity,
            final String compensationId) {

        final CompensateStockRequest request = CompensateStockRequest.newBuilder()
                .setProductId(productId)
                .setQuantity(quantity)
                .setCompensationId(compensationId)
                .build();

        try {
            final ProductResponse response =
                    productServiceBlockingStub.compensateStock(request);

            return productGrpcMapper.toProductResponseDto(response);
        } catch (final StatusRuntimeException exception) {
            throw mapStockOperationException(exception, productId);
        }
    }

    @SuppressWarnings("PMD.LawOfDemeter")
    private Status.Code getStatusCode(final StatusRuntimeException exception) {
        final Status status = exception.getStatus();
        return status.getCode();
    }

    private RuntimeException mapStockOperationException(
            final StatusRuntimeException exception,
            final Long productId) {

        final Status.Code statusCode = getStatusCode(exception);
        final RuntimeException mappedException;

        if (statusCode == Status.Code.NOT_FOUND) {
            mappedException = new ProductNotFoundException(productId, exception);
        } else if (statusCode == Status.Code.FAILED_PRECONDITION) {
            mappedException = new InsufficientStockException(productId, exception);
        } else if (statusCode == Status.Code.ABORTED) {
            mappedException = new ProductConcurrencyException(productId, exception);
        } else {
            mappedException = exception;
        }

        return mappedException;
    }
}
