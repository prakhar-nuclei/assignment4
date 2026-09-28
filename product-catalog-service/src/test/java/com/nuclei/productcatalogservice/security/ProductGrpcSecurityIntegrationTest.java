package com.nuclei.productcatalogservice.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import com.nuclei.product.proto.GetProductRequest;
import com.nuclei.product.proto.ProductServiceGrpc;
import com.nuclei.productcatalogservice.dto.ProductResponseDto;
import com.nuclei.productcatalogservice.service.ProductService;
import java.math.BigDecimal;
import java.util.List;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.grpc.client.ChannelBuilderOptions;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.grpc.client.interceptor.security.BearerTokenAuthenticationInterceptor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@AutoConfigureTestGrpcTransport
class ProductGrpcSecurityIntegrationTest {

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ProductService productService;

    @Autowired
    private GrpcChannelFactory grpcChannelFactory;

    @Test
    void shouldRejectGrpcRequestWithoutAuthorization() {
        final ManagedChannel channel =
                grpcChannelFactory.createChannel("local");

        final ProductServiceGrpc.ProductServiceBlockingStub stub =
                ProductServiceGrpc.newBlockingStub(channel);

        final StatusRuntimeException exception = assertThrows(
                StatusRuntimeException.class,
                () -> stub.getProduct(
                        GetProductRequest.newBuilder()
                                .setProductId(1L)
                                .build()
                )
        );

        assertEquals(
                Status.Code.UNAUTHENTICATED,
                exception.getStatus().getCode()
        );

        channel.shutdownNow();
    }

    @Test
    void shouldAllowGrpcRequestWithValidJwt() {
        final Jwt jwt = Jwt.withTokenValue("valid-token")
                .header("alg", "RS256")
                .claim("sub", "test-user")
                .build();

        when(jwtDecoder.decode("valid-token"))
                .thenReturn(jwt);

        final ProductResponseDto product =
                new ProductResponseDto(
                        1L,
                        "Test Product",
                        new BigDecimal("99.99"),
                        10
                );

        when(productService.getProduct(1L))
                .thenReturn(product);

        final BearerTokenAuthenticationInterceptor authInterceptor =
                new BearerTokenAuthenticationInterceptor("valid-token");

        final ChannelBuilderOptions options =
                ChannelBuilderOptions.defaults()
                        .withInterceptors(List.of(authInterceptor));

        final ManagedChannel channel =
                grpcChannelFactory.createChannel("local", options);

        final ProductServiceGrpc.ProductServiceBlockingStub stub =
                ProductServiceGrpc.newBlockingStub(channel);

        final var response = stub.getProduct(
                GetProductRequest.newBuilder()
                        .setProductId(1L)
                        .build()
        );

        assertEquals(1L, response.getProductId());
        assertEquals("Test Product", response.getName());
        assertEquals("99.99", response.getPrice());
        assertEquals(10, response.getStock());

        channel.shutdownNow();
    }
}