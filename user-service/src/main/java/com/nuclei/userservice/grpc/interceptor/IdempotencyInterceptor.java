package com.nuclei.userservice.grpc.interceptor;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import org.springframework.stereotype.Component;

@Component
public class IdempotencyInterceptor implements ServerInterceptor {

    public static final Context.Key<String> IDEMPOTENCY_KEY =
            Context.key("idempotency-key");

    private static final Metadata.Key<String> IDEMPOTENCY_METADATA_KEY =
            Metadata.Key.of(
                    "idempotency-key",
                    Metadata.ASCII_STRING_MARSHALLER
            );

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            final ServerCall<ReqT, RespT> call,
            final Metadata headers,
            final ServerCallHandler<ReqT, RespT> next) {

        if (!call.getMethodDescriptor()
                .getFullMethodName()
                .endsWith("/CreateUser")) {
            return next.startCall(call, headers);
        }

        final String idempotencyKey =
                headers.get(IDEMPOTENCY_METADATA_KEY);

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            call.close(
                    Status.INVALID_ARGUMENT
                            .withDescription("Idempotency-Key is required"),
                    new Metadata()
            );
            return new ServerCall.Listener<>() {
            };
        }

        final Context context =
                Context.current().withValue(
                        IDEMPOTENCY_KEY,
                        idempotencyKey
                );

        return Contexts.interceptCall(
                context,
                call,
                headers,
                next
        );
    }
}