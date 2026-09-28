package com.nuclei.productcatalogservice.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class GrpcJwtAuthInterceptorTest {

    private static final Metadata.Key<String> AUTHORIZATION_METADATA_KEY =
            Metadata.Key.of(
                    "authorization",
                    Metadata.ASCII_STRING_MARSHALLER
            );

    private JwtDecoder jwtDecoder;
    private GrpcJwtAuthInterceptor interceptor;
    private ServerCall<Object, Object> serverCall;
    private ServerCallHandler<Object, Object> next;

    @BeforeEach
    void setUp() {
        jwtDecoder = mock(JwtDecoder.class);
        interceptor = new GrpcJwtAuthInterceptor(jwtDecoder);
        serverCall = mock(ServerCall.class);
        next = mock(ServerCallHandler.class);

        final MethodDescriptor<Object, Object> methodDescriptor =
                mock(MethodDescriptor.class);

        when(serverCall.getMethodDescriptor()).thenReturn(methodDescriptor);
        when(methodDescriptor.getFullMethodName())
                .thenReturn("product.ProductService/GetProduct");
    }

    @Test
    void shouldRejectRequestWhenAuthorizationHeaderIsMissing() {
        final Metadata headers = new Metadata();

        interceptor.interceptCall(serverCall, headers, next);

        verifyUnauthenticated();
        verifyNoInteractions(jwtDecoder, next);
    }

    @Test
    void shouldRejectRequestWhenAuthorizationHeaderIsMalformed() {
        final Metadata headers = new Metadata();
        headers.put(AUTHORIZATION_METADATA_KEY, "InvalidToken");

        interceptor.interceptCall(serverCall, headers, next);

        verifyUnauthenticated();
        verifyNoInteractions(jwtDecoder, next);
    }

    @Test
    void shouldRejectRequestWhenBearerTokenIsEmpty() {
        final Metadata headers = new Metadata();
        headers.put(AUTHORIZATION_METADATA_KEY, "Bearer ");

        interceptor.interceptCall(serverCall, headers, next);

        verifyUnauthenticated();
        verifyNoInteractions(jwtDecoder, next);
    }

    private void verifyUnauthenticated() {
        final ArgumentCaptor<Status> statusCaptor =
                ArgumentCaptor.forClass(Status.class);

        final ArgumentCaptor<Metadata> metadataCaptor =
                ArgumentCaptor.forClass(Metadata.class);

        verify(serverCall).close(
                statusCaptor.capture(),
                metadataCaptor.capture()
        );

        assertEquals(Status.Code.UNAUTHENTICATED, statusCaptor.getValue().getCode());
        assertEquals(
                "Missing or invalid authorization header",
                statusCaptor.getValue().getDescription()
        );
    }

    @Test
    void shouldRejectRequestWhenJwtIsInvalid() {
        final Metadata headers = new Metadata();
        headers.put(
                AUTHORIZATION_METADATA_KEY,
                "Bearer invalid-token"
        );

        when(jwtDecoder.decode("invalid-token"))
                .thenThrow(new JwtException("Invalid token"));

        interceptor.interceptCall(serverCall, headers, next);

        final ArgumentCaptor<Status> statusCaptor =
                ArgumentCaptor.forClass(Status.class);

        final ArgumentCaptor<Metadata> metadataCaptor =
                ArgumentCaptor.forClass(Metadata.class);

        verify(serverCall).close(
                statusCaptor.capture(),
                metadataCaptor.capture()
        );

        assertEquals(
                Status.Code.UNAUTHENTICATED,
                statusCaptor.getValue().getCode()
        );
        assertEquals(
                "Invalid access token",
                statusCaptor.getValue().getDescription()
        );

        verify(next, org.mockito.Mockito.never())
                .startCall(serverCall, headers);

        verify(next, never()).startCall(serverCall, headers);
    }

    @Test
    void shouldAllowRequestWhenJwtIsValid() {
        final Metadata headers = new Metadata();
        headers.put(
                AUTHORIZATION_METADATA_KEY,
                "Bearer valid-token"
        );

        final Jwt jwt = mock(Jwt.class);

        when(jwtDecoder.decode("valid-token"))
                .thenReturn(jwt);

        interceptor.interceptCall(serverCall, headers, next);

        verify(jwtDecoder).decode("valid-token");
        verify(next).startCall(serverCall, headers);
    }

    @Test
    void shouldPropagateAuthenticatedJwtThroughGrpcContext() {
        final Metadata headers = new Metadata();
        headers.put(
                AUTHORIZATION_METADATA_KEY,
                "Bearer valid-token"
        );

        final Jwt jwt = mock(Jwt.class);

        when(jwtDecoder.decode("valid-token"))
                .thenReturn(jwt);

        when(next.startCall(serverCall, headers))
                .thenAnswer(invocation -> {
                    assertEquals(jwt, GrpcJwtAuthInterceptor.getAuthenticatedJwt());
                    return new ServerCall.Listener<>() {
                    };
                });

        interceptor.interceptCall(serverCall, headers, next);

        verify(jwtDecoder).decode("valid-token");
        verify(next).startCall(serverCall, headers);
    }

    @Test
    void shouldAcceptBearerSchemeCaseInsensitively() {
        final Metadata headers = new Metadata();
        headers.put(
                AUTHORIZATION_METADATA_KEY,
                "bearer valid-token"
        );

        final Jwt jwt = mock(Jwt.class);

        when(jwtDecoder.decode("valid-token"))
                .thenReturn(jwt);

        interceptor.interceptCall(serverCall, headers, next);

        verify(jwtDecoder).decode("valid-token");
        verify(next).startCall(serverCall, headers);
    }

    @Test
    void shouldAllowGrpcReflectionWithoutAuthentication() {
        final MethodDescriptor<Object, Object> reflectionMethod =
                mock(MethodDescriptor.class);

        when(serverCall.getMethodDescriptor())
                .thenReturn(reflectionMethod);

        when(reflectionMethod.getFullMethodName())
                .thenReturn(
                        "grpc.reflection.v1.ServerReflection/ServerReflectionInfo"
                );

        final Metadata headers = new Metadata();

        interceptor.interceptCall(serverCall, headers, next);

        verify(next).startCall(serverCall, headers);
        verifyNoInteractions(jwtDecoder);
    }

    @Test
    void shouldRejectProtectedRpcWithoutAuthentication() {
        final Metadata headers = new Metadata();

        interceptor.interceptCall(serverCall, headers, next);

        verifyUnauthenticated();
        verifyNoInteractions(jwtDecoder, next);
    }
}