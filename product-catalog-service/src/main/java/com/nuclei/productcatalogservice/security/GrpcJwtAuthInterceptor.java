package com.nuclei.productcatalogservice.security;

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

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String REFLECTION_METHOD =
            "grpc.reflection.v1.ServerReflection/ServerReflectionInfo";

    private static final String REFLECTION_V1_ALPHA_METHOD =
            "grpc.reflection.v1alpha.ServerReflection/ServerReflectionInfo";

    private static final Metadata.Key<String> AUTHORIZATION_METADATA_KEY =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

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

        if (REFLECTION_METHOD.equals(methodName)
                || REFLECTION_V1_ALPHA_METHOD.equals(methodName)) {
            return next.startCall(call, headers);
        }

        final String authorization =
                headers.get(AUTHORIZATION_METADATA_KEY);

        if (!isValidBearerHeader(authorization)) {
            return unauthenticated(call, "Missing or invalid authorization header");
        }

        final String token = authorization.substring(BEARER_PREFIX.length()).trim();

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
        }  catch (final JwtException exception) {
        return unauthenticated(call, "Invalid access token");
       }
    }


    public static Jwt getAuthenticatedJwt() {
        return JWT_CONTEXT_KEY.get();
    }

    private static boolean isValidBearerHeader(final String authorization) {
        return authorization != null
                && authorization.regionMatches(
                true,
                0,
                BEARER_PREFIX,
                0,
                BEARER_PREFIX.length())
                && !authorization.substring(BEARER_PREFIX.length()).isBlank();
    }

    private static <ReqT, RespT> ServerCall.Listener<ReqT> unauthenticated(
            final ServerCall<ReqT, RespT> call,
            final String description) {

        call.close(
                Status.UNAUTHENTICATED.withDescription(description),
                new Metadata()
        );

        return new ServerCall.Listener<>() {
        };
    }
}