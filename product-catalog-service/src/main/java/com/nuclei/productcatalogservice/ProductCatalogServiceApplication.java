package com.nuclei.productcatalogservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.grpc.server.autoconfigure.security.GrpcServerOAuth2ResourceServerAutoConfiguration;

@SpringBootApplication(
        exclude = GrpcServerOAuth2ResourceServerAutoConfiguration.class)
public final class ProductCatalogServiceApplication {

    private ProductCatalogServiceApplication() {
    }

    public static void main(final String[] args) {
        SpringApplication.run(ProductCatalogServiceApplication.class, args);
    }

}
