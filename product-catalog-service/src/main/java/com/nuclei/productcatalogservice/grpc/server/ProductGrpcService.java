package com.nuclei.productcatalogservice.grpc.server;

import com.nuclei.product.proto.CreateProductRequest;
import com.nuclei.product.proto.DeleteProductRequest;
import com.nuclei.product.proto.GetProductRequest;
import com.nuclei.product.proto.ProductResponse;
import com.nuclei.product.proto.ProductServiceGrpc;
import com.nuclei.product.proto.UpdateProductRequest;
import com.nuclei.product.proto.UpdateStockRequest;
import com.nuclei.productcatalogservice.dto.ProductResponseDto;
import com.nuclei.productcatalogservice.exception.InsufficientStockException;
import com.nuclei.productcatalogservice.exception.InvalidStockOperationException;
import com.nuclei.productcatalogservice.exception.ProductConcurrencyException;
import com.nuclei.productcatalogservice.exception.ProductNotFoundException;
import com.nuclei.productcatalogservice.mapper.ProductGrpcMapper;
import com.nuclei.productcatalogservice.service.ProductService;
import java.math.BigDecimal;
import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
public class ProductGrpcService extends ProductServiceGrpc.ProductServiceImplBase {

    private final ProductService productService;
    private final ProductGrpcMapper productGrpcMapper;
    private static final String INVALID_PRODUCT_ID_MESSAGE =
            "Product ID must be greater than zero";

    public ProductGrpcService(
            final ProductService productService,
            final ProductGrpcMapper productGrpcMapper) {
        this.productService = productService;
        this.productGrpcMapper = productGrpcMapper;
    }

    @Override
    public void getProduct(
            GetProductRequest request,
            StreamObserver<ProductResponse> responseObserver) {

        if (request.getProductId() <= 0) {
            sendError(
                    responseObserver,
                    Status.INVALID_ARGUMENT,
                    INVALID_PRODUCT_ID_MESSAGE
            );
            return;
        }

        try {
            ProductResponseDto product =
                    productService.getProduct(request.getProductId());

            responseObserver.onNext(productGrpcMapper.toProductResponse(product));
            responseObserver.onCompleted();
        } catch (ProductNotFoundException exception) {
            sendError(
                    responseObserver,
                    Status.NOT_FOUND,
                    exception.getMessage()
            );
        }
    }

    @Override
    public void createProduct(
            final CreateProductRequest request,
            final StreamObserver<ProductResponse> responseObserver) {

        try {
            ProductResponseDto product =
                    productService.createProduct(
                            request.getName(),
                            new BigDecimal(request.getPrice()),
                            request.getStock()
                    );

            responseObserver.onNext(
                    productGrpcMapper.toProductResponse(product)
            );
            responseObserver.onCompleted();
        } catch (NumberFormatException exception) {
            sendError(
                    responseObserver,
                    Status.INVALID_ARGUMENT,
                    "Price must be a valid monetary value"
            );
        }
    }

    @Override
    public void updateProduct(
            final UpdateProductRequest request,
            final StreamObserver<ProductResponse> responseObserver) {

        if (request.getProductId() <= 0) {
            sendError(
                    responseObserver,
                    Status.INVALID_ARGUMENT,
                    INVALID_PRODUCT_ID_MESSAGE
            );
            return;
        }

        try {
            ProductResponseDto product =
                    productService.updateProduct(
                            request.getProductId(),
                            request.getName(),
                            new BigDecimal(request.getPrice()),
                            request.getStock()
                    );

            responseObserver.onNext(
                    productGrpcMapper.toProductResponse(product)
            );
            responseObserver.onCompleted();
        } catch (NumberFormatException exception) {
            sendError(
                    responseObserver,
                    Status.INVALID_ARGUMENT,
                    "Price must be a valid monetary value"
            );
        } catch (ProductNotFoundException exception) {
            sendError(
                    responseObserver,
                    Status.NOT_FOUND,
                    exception.getMessage()
            );
        } catch (ProductConcurrencyException exception) {
            sendError(
                    responseObserver,
                    Status.ABORTED,
                    exception.getMessage()
            );
        }
    }

    @Override
    public void deleteProduct(
            final DeleteProductRequest request,
            final StreamObserver<Empty> responseObserver) {

        if (request.getProductId() <= 0) {
            sendError(
                    responseObserver,
                    Status.INVALID_ARGUMENT,
                    INVALID_PRODUCT_ID_MESSAGE
            );
            return;
        }

        try {
            productService.deleteProduct(request.getProductId());

            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (ProductNotFoundException exception) {
            sendError(
                    responseObserver,
                    Status.NOT_FOUND,
                    exception.getMessage()
            );
        } catch (ProductConcurrencyException exception) {
            sendError(
                    responseObserver,
                    Status.ABORTED,
                    exception.getMessage()
            );
        }
    }

    @Override
    public void updateStock(
            UpdateStockRequest request,
            StreamObserver<ProductResponse> responseObserver) {

        if (request.getProductId() <= 0) {
            sendError(
                    responseObserver,
                    Status.INVALID_ARGUMENT,
                    INVALID_PRODUCT_ID_MESSAGE
            );
            return;
        }

        try {
            ProductResponseDto product =
                    productService.updateStock(
                            request.getProductId(),
                            request.getQuantity()
                    );

            responseObserver.onNext(productGrpcMapper.toProductResponse(product));
            responseObserver.onCompleted();
        } catch (InvalidStockOperationException exception) {
            sendError(
                    responseObserver,
                    Status.INVALID_ARGUMENT,
                    exception.getMessage()
            );
        } catch (ProductNotFoundException exception) {
            sendError(
                    responseObserver,
                    Status.NOT_FOUND,
                    exception.getMessage()
            );
        } catch (InsufficientStockException exception) {
            sendError(
                    responseObserver,
                    Status.FAILED_PRECONDITION,
                    exception.getMessage()
            );
        } catch (ProductConcurrencyException exception) {
            sendError(
                    responseObserver,
                    Status.ABORTED,
                    exception.getMessage()
            );
        }
    }

    private <T> void sendError(
            final StreamObserver<T> responseObserver,
            final Status status,
            final String description) {
        responseObserver.onError(
                status
                        .withDescription(description)
                        .asRuntimeException()
        );
    }
}