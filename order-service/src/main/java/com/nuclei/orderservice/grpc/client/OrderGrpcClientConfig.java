package com.nuclei.orderservice.grpc.client;

import com.nuclei.product.proto.ProductServiceGrpc;
import com.nuclei.user.proto.UserServiceGrpc;
import lombok.NoArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
}