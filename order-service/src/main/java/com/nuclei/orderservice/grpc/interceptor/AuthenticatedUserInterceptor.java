package com.nuclei.orderservice.grpc.interceptor;

import com.nuclei.orderservice.exception.InvalidOrderRequestException;
import com.nuclei.orderservice.grpc.metadata.GrpcUserContext;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.stereotype.Component;

@Component
@GlobalServerInterceptor
public class AuthenticatedUserInterceptor implements ServerInterceptor {

    public static final Context.Key<Long> USER_ID_CONTEXT_KEY =
            Context.key("authenticated-user-id");

    private final GrpcUserContext grpcUserContext;

    public AuthenticatedUserInterceptor(final GrpcUserContext grpcUserContext) {
        this.grpcUserContext = grpcUserContext;
    }

    @Override
    public <R, S> ServerCall.Listener<R> interceptCall(
            final ServerCall<R, S> call,
            final Metadata headers,
            final ServerCallHandler<R, S> next) {

        ServerCall.Listener<R> listener;

        try {
            final Long userId = grpcUserContext.getUserId(headers);

            final Context context = Context.current()
                    .withValue(USER_ID_CONTEXT_KEY, userId);

            listener = Contexts.interceptCall(
                    context,
                    call,
                    headers,
                    next
            );
        } catch (final InvalidOrderRequestException exception) {
            call.close(
                    Status.UNAUTHENTICATED.withDescription(
                            exception.getMessage()
                    ),
                    new Metadata()
            );

            listener = new ServerCall.Listener<>() {
            };
        }

        return listener;
    }
}