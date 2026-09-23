package com.nuclei.userservice.security;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.*;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class GrpcJwtAuthInterceptorTest {

    @Mock
    private JwtDecoder jwtDecoder;

    @Mock
    private ServerCall<String, String> serverCall;

    @Mock
    private ServerCallHandler<String, String> next;

    @Mock
    private MethodDescriptor<String, String> methodDescriptor;

    @Mock
    private Listener<String> listener;

    private GrpcJwtAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        interceptor = new GrpcJwtAuthInterceptor(jwtDecoder);
    }

    @Test
    void interceptCall_shouldAllowCreateUserWithoutJwt() {
        final Metadata headers = new Metadata();

        when(serverCall.getMethodDescriptor())
                .thenReturn(methodDescriptor);

        when(methodDescriptor.getFullMethodName())
                .thenReturn(
                        "user.UserService/CreateUser"
                );

        when(next.startCall(serverCall, headers))
                .thenReturn(listener);

        interceptor.interceptCall(
                serverCall,
                headers,
                next
        );

        verify(next).startCall(serverCall, headers);
        verifyNoInteractions(jwtDecoder);
    }

    @Test
    void interceptCall_shouldRejectProtectedMethodWithoutJwt() {
        final Metadata headers = new Metadata();

        when(serverCall.getMethodDescriptor())
                .thenReturn(methodDescriptor);

        when(methodDescriptor.getFullMethodName())
                .thenReturn(
                        "user.UserService/GetUser"
                );

        interceptor.interceptCall(
                serverCall,
                headers,
                next
        );

        verify(serverCall).close(
                argThat(status ->
                        status.getCode() == Status.Code.UNAUTHENTICATED
                                && "Missing or invalid authorization header"
                                .equals(status.getDescription())
                ),
                any(Metadata.class)
        );

        verifyNoInteractions(jwtDecoder);
    }

    @Test
    void interceptCall_shouldRejectInvalidJwt() {
        final Metadata headers = new Metadata();

        headers.put(
                Metadata.Key.of(
                        "authorization",
                        Metadata.ASCII_STRING_MARSHALLER
                ),
                "Bearer invalid-token"
        );

        when(serverCall.getMethodDescriptor())
                .thenReturn(methodDescriptor);

        when(methodDescriptor.getFullMethodName())
                .thenReturn(
                        "user.UserService/GetUser"
                );

        when(jwtDecoder.decode("invalid-token"))
                .thenThrow(new JwtException("Invalid token"));

        interceptor.interceptCall(
                serverCall,
                headers,
                next
        );

        verify(jwtDecoder).decode("invalid-token");

        verify(serverCall).close(
                argThat(status ->
                        status.getCode() == Status.Code.UNAUTHENTICATED
                                && "Invalid access token"
                                .equals(status.getDescription())
                ),
                any(Metadata.class)
        );
    }

    @Test
    void interceptCall_shouldAllowValidJwt() {
        final Metadata headers = new Metadata();

        headers.put(
                Metadata.Key.of(
                        "authorization",
                        Metadata.ASCII_STRING_MARSHALLER
                ),
                "Bearer valid-token"
        );

        when(serverCall.getMethodDescriptor())
                .thenReturn(methodDescriptor);

        when(methodDescriptor.getFullMethodName())
                .thenReturn(
                        "user.UserService/GetUser"
                );

        final Jwt jwt = Jwt.withTokenValue("valid-token")
                .header("alg", "RS256")
                .claim("sub", "1")
                .build();

        when(jwtDecoder.decode("valid-token"))
                .thenReturn(jwt);

        when(next.startCall(serverCall, headers))
                .thenReturn(listener);

        interceptor.interceptCall(
                serverCall,
                headers,
                next
        );

        verify(jwtDecoder).decode("valid-token");
        verify(next).startCall(serverCall, headers);
    }
}