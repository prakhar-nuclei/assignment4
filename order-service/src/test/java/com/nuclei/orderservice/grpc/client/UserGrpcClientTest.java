package com.nuclei.orderservice.grpc.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import com.nuclei.orderservice.dtos.UserResponseDto;
import com.nuclei.orderservice.exception.UserNotFoundException;
import com.nuclei.orderservice.mapper.UserGrpcMapper;
import com.nuclei.user.proto.GetUserRequest;
import com.nuclei.user.proto.UserResponse;
import com.nuclei.user.proto.UserServiceGrpc;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserGrpcClientTest {

    @Mock
    private UserServiceGrpc.UserServiceBlockingStub userServiceBlockingStub;

    @Mock
    private UserGrpcMapper userGrpcMapper;

    private UserGrpcClient userGrpcClient;

    @BeforeEach
    void setUp() {
        userGrpcClient = new UserGrpcClient(
                userServiceBlockingStub,
                userGrpcMapper);
    }


    @Test
    void getUserShouldReturnMappedUserResponse() {
        final Long userId = 10L;

        final UserResponse grpcResponse =
                UserResponse.newBuilder()
                        .setUserId(userId)
                        .setName("Prakhar")
                        .setEmail("prakhar@example.com")
                        .build();

        final UserResponseDto expectedResponse =
                new UserResponseDto(
                        userId,
                        "Prakhar",
                        "prakhar@example.com");

        when(userServiceBlockingStub.getUser(any(GetUserRequest.class)))
                .thenReturn(grpcResponse);

        when(userGrpcMapper.toUserResponseDto(grpcResponse))
                .thenReturn(expectedResponse);

        final UserResponseDto actualResponse =
                userGrpcClient.getUser(userId);

        assertSame(expectedResponse, actualResponse);

        final ArgumentCaptor<GetUserRequest> requestCaptor =
                ArgumentCaptor.forClass(GetUserRequest.class);

        verify(userServiceBlockingStub)
                .getUser(requestCaptor.capture());

        assertEquals(
                userId,
                requestCaptor.getValue().getUserId());

        verify(userGrpcMapper)
                .toUserResponseDto(grpcResponse);
    }

    @Test
    void getUserShouldThrowUserNotFoundExceptionWhenUserDoesNotExist() {
        final Long userId = 10L;

        final StatusRuntimeException grpcException =
                Status.NOT_FOUND
                        .withDescription("User not found")
                        .asRuntimeException();

        when(userServiceBlockingStub.getUser(any(GetUserRequest.class)))
                .thenThrow(grpcException);

        assertThrows(
                UserNotFoundException.class,
                () -> userGrpcClient.getUser(userId));

        verify(userServiceBlockingStub)
                .getUser(any(GetUserRequest.class));

        verifyNoInteractions(userGrpcMapper);
    }

    @Test
    void getUserShouldPropagateGrpcExceptionWhenStatusIsNotNotFound() {
        final Long userId = 10L;

        final StatusRuntimeException grpcException =
                Status.UNAVAILABLE
                        .withDescription("User service unavailable")
                        .asRuntimeException();

        when(userServiceBlockingStub.getUser(any(GetUserRequest.class)))
                .thenThrow(grpcException);

        final StatusRuntimeException exception =
                assertThrows(
                        StatusRuntimeException.class,
                        () -> userGrpcClient.getUser(userId));

        assertSame(grpcException, exception);

        verify(userServiceBlockingStub)
                .getUser(any(GetUserRequest.class));

        verifyNoInteractions(userGrpcMapper);
    }
}