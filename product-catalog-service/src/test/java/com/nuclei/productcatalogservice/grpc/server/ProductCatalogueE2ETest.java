package com.nuclei.productcatalogservice.grpc.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.nuclei.product.proto.CreateProductRequest;
import com.nuclei.product.proto.GetProductRequest;
import com.nuclei.product.proto.ProductResponse;
import com.nuclei.product.proto.ProductServiceGrpc;
import io.grpc.ManagedChannel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.grpc.client.GrpcChannelFactory;

@SpringBootTest
@AutoConfigureTestGrpcTransport
class ProductCatalogueE2ETest {

    @Autowired
    private GrpcChannelFactory grpcChannelFactory;

    @Test
    void shouldCreateAndRetrieveProductThroughGrpc() {
        final ManagedChannel channel =
                grpcChannelFactory.createChannel("local");

        try {
            final ProductServiceGrpc.ProductServiceBlockingStub stub =
                    ProductServiceGrpc.newBlockingStub(channel);

            final CreateProductRequest createRequest =
                    CreateProductRequest.newBuilder()
                            .setName("E2E Test Product")
                            .setPrice("199.99")
                            .setStock(20)
                            .build();

            final ProductResponse createdProduct =
                    stub.createProduct(createRequest);

            assertTrue(createdProduct.getProductId() > 0);
            assertEquals(
                    "E2E Test Product",
                    createdProduct.getName()
            );
            assertEquals("199.99", createdProduct.getPrice());
            assertEquals(20, createdProduct.getStock());

            final GetProductRequest getRequest =
                    GetProductRequest.newBuilder()
                            .setProductId(createdProduct.getProductId())
                            .build();

            final ProductResponse fetchedProduct =
                    stub.getProduct(getRequest);

            assertEquals(
                    createdProduct.getProductId(),
                    fetchedProduct.getProductId()
            );
            assertEquals(
                    "E2E Test Product",
                    fetchedProduct.getName()
            );
            assertEquals("199.99", fetchedProduct.getPrice());
            assertEquals(20, fetchedProduct.getStock());
        } finally {
            channel.shutdownNow();
        }
    }
}
