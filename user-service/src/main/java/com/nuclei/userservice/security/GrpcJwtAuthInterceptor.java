package com.nuclei.userservice.security;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

@Component
@GlobalServerInterceptor
public class GrpcJwtAuthInterceptor implements ServerInterceptor {

    private static final Metadata.Key<String> AUTHORIZATION_METADATA_KEY =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final String CREATE_USER_METHOD =
            "user.UserService/CreateUser";

    private static final String AUTHENTICATE_USER_METHOD =
            "user.UserService/AuthenticateUser";
    private static final String REFLECTION_METHOD =
            "grpc.reflection.v1.ServerReflection/ServerReflectionInfo";

    private static final Context.Key<Jwt> JWT_CONTEXT_KEY =
            Context.key("jwt");

    private final JwtDecoder jwtDecoder;

    public GrpcJwtAuthInterceptor(final JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            final ServerCall<ReqT, RespT> call,
            final Metadata headers,
            final ServerCallHandler<ReqT, RespT> next) {

        final String methodName =
                call.getMethodDescriptor().getFullMethodName();

        if (CREATE_USER_METHOD.equals(methodName)
                || AUTHENTICATE_USER_METHOD.equals(methodName)
                || REFLECTION_METHOD.equals(methodName)) {
            return next.startCall(call, headers);
        }

        final String authorization =
                headers.get(AUTHORIZATION_METADATA_KEY);

        if (authorization == null
                || !authorization.startsWith("Bearer ")) {

            call.close(
                    Status.UNAUTHENTICATED
                            .withDescription("Missing or invalid authorization header"),
                    new Metadata()
            );

            return new ServerCall.Listener<>() {};
        }

        final String token = authorization.substring("Bearer ".length());

        try {
            final Jwt jwt = jwtDecoder.decode(token);

            final Context context =
                    Context.current().withValue(JWT_CONTEXT_KEY, jwt);

            return Contexts.interceptCall(
                    context,
                    call,
                    headers,
                    next
            );

        } catch (final JwtException exception)  {

            call.close(
                    Status.UNAUTHENTICATED
                            .withDescription("Invalid access token"),
                    new Metadata()
            );

            return new ServerCall.Listener<>() {};
        }
    }

    public static Jwt getAuthenticatedJwt() {
        return JWT_CONTEXT_KEY.get();
    }
}