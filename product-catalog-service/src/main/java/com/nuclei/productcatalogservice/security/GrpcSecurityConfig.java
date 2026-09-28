package com.nuclei.productcatalogservice.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.grpc.server.security.AuthenticationProcessInterceptor;
import org.springframework.grpc.server.security.GrpcSecurity;

@Configuration
public class GrpcSecurityConfig {

    @Bean
    @GlobalServerInterceptor
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    public AuthenticationProcessInterceptor authenticationProcessInterceptor(
            final GrpcSecurity grpc) throws Exception {

        return grpc
                .authorizeRequests(requests -> requests
                        .methods("grpc.*/*").permitAll()
                        .allRequests().permitAll())
                .build();
    }
}