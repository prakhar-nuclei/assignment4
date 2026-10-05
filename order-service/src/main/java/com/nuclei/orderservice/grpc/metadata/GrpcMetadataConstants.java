package com.nuclei.orderservice.grpc.metadata;

import io.grpc.Metadata;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public final class GrpcMetadataConstants {

    public static final Metadata.Key<String> AUTHENTICATED_USER_ID =
            Metadata.Key.of(
                    "authenticated-user-id",
                    Metadata.ASCII_STRING_MARSHALLER
            );
}