package com.nuclei.productcatalogservice.grpc.server;

import com.nuclei.product.proto.GetProductRequest;
import com.nuclei.product.proto.ProductResponse;
import com.nuclei.product.proto.ProductServiceGrpc;
import com.nuclei.product.proto.UpdateStockRequest;
import com.nuclei.productcatalogservice.dto.ProductResponseDto;
import com.nuclei.productcatalogservice.exception.InsufficientStockException;
import com.nuclei.productcatalogservice.exception.InvalidStockOperationException;
import com.nuclei.productcatalogservice.exception.ProductConcurrencyException;
import com.nuclei.productcatalogservice.exception.ProductNotFoundException;
import com.nuclei.productcatalogservice.service.ProductService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
public class ProductGrpcService extends ProductServiceGrpc.ProductServiceImplBase {

    private final ProductService productService;

    public ProductGrpcService(ProductService productService) {
        this.productService = productService;
    }

    @Override
    public void getProduct(
            GetProductRequest request,
            StreamObserver<ProductResponse> responseObserver) {

        if (request.getProductId() <= 0) {
            sendError(
                    responseObserver,
                    Status.INVALID_ARGUMENT,
                    "Product ID must be greater than zero"
            );
            return;
        }

        try {
            ProductResponseDto product =
                    productService.getProduct(request.getProductId());

            responseObserver.onNext(toProductResponse(product));
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
    public void updateStock(
            UpdateStockRequest request,
            StreamObserver<ProductResponse> responseObserver) {

        if (request.getProductId() <= 0) {
            sendError(
                    responseObserver,
                    Status.INVALID_ARGUMENT,
                    "Product ID must be greater than zero"
            );
            return;
        }

        try {
            ProductResponseDto product =
                    productService.updateStock(
                            request.getProductId(),
                            request.getQuantity()
                    );

            responseObserver.onNext(toProductResponse(product));
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

    private ProductResponse toProductResponse(ProductResponseDto product) {
        return ProductResponse.newBuilder()
                .setProductId(product.productId())
                .setName(product.name())
                .setPrice(product.price().toPlainString())
                .setStock(product.stock())
                .build();
    }

    private void sendError(
            StreamObserver<ProductResponse> responseObserver,
            Status status,
            String description) {
        responseObserver.onError(
                status
                        .withDescription(description)
                        .asRuntimeException()
        );
    }
}