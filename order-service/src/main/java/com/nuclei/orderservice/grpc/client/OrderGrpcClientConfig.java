package com.nuclei.orderservice.grpc.client;

import com.nuclei.product.proto.ProductServiceGrpc;
import com.nuclei.user.proto.UserServiceGrpc;
import java.util.List;
import java.util.Map;
import io.grpc.ManagedChannelBuilder;
import lombok.NoArgsConstructor;
import org.springframework.boot.grpc.client.autoconfigure.GrpcClientDefaultServiceConfigCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelBuilderCustomizer;
import org.springframework.grpc.client.GrpcChannelFactory;

@Configuration
@NoArgsConstructor
public class OrderGrpcClientConfig {

    @Bean
    public UserServiceGrpc.UserServiceBlockingStub userServiceBlockingStub(
            final GrpcChannelFactory grpcChannelFactory) {
        return UserServiceGrpc.newBlockingStub(
                grpcChannelFactory.createChannel("user-service")
        );
    }

    @Bean
    public ProductServiceGrpc.ProductServiceBlockingStub productServiceBlockingStub(
            final GrpcChannelFactory grpcChannelFactory) {
        return ProductServiceGrpc.newBlockingStub(
                grpcChannelFactory.createChannel("product-catalog-service")
        );
    }

    @Bean
    public GrpcChannelBuilderCustomizer<?> productCatalogRetryCustomizer() {
        return GrpcChannelBuilderCustomizer.matching(
                "product-catalog-service",
                ManagedChannelBuilder::enableRetry
        );

    }

    @Bean
    public GrpcClientDefaultServiceConfigCustomizer productGetProductRetryPolicy() {
        return (target, defaultServiceConfig) ->
                defaultServiceConfig.put(
                        "methodConfig",
                        List.of(
                                Map.of(
                                        "name",
                                        List.of(
                                                Map.of(
                                                        "service", "product.ProductService",
                                                        "method", "GetProduct"
                                                )
                                        ),
                                        "retryPolicy",
                                        Map.of(
                                                "maxAttempts", 3,
                                                "initialBackoff", "0.1s",
                                                "maxBackoff", "1s",
                                                "backoffMultiplier", 2.0,
                                                "retryableStatusCodes",
                                                List.of("UNAVAILABLE")
                                        )
                                )
                        )
                );
    }
}
