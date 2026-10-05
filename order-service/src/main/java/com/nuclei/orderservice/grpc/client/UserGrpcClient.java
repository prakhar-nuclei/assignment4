package com.nuclei.orderservice.grpc.client;

import com.nuclei.orderservice.dtos.UserResponseDto;
import com.nuclei.orderservice.exception.UserNotFoundException;
import com.nuclei.orderservice.mapper.UserGrpcMapper;
import com.nuclei.user.proto.GetUserRequest;
import com.nuclei.user.proto.UserResponse;
import com.nuclei.user.proto.UserServiceGrpc;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.stereotype.Component;

@Component
public class UserGrpcClient {

    private final UserServiceGrpc.UserServiceBlockingStub userServiceBlockingStub;
    private final UserGrpcMapper userGrpcMapper;

    public UserGrpcClient(
            final UserServiceGrpc.UserServiceBlockingStub userServiceBlockingStub,
            final UserGrpcMapper userGrpcMapper) {
        this.userServiceBlockingStub = userServiceBlockingStub;
        this.userGrpcMapper = userGrpcMapper;
    }

    public UserResponseDto getUser(final Long userId) {
        final GetUserRequest request = GetUserRequest.newBuilder()
                .setUserId(userId)
                .build();

        try {
            final UserResponse response = userServiceBlockingStub.getUser(request);
            return userGrpcMapper.toUserResponseDto(response);
        } catch (final StatusRuntimeException exception) {
             if (getStatusCode(exception) == Status.Code.NOT_FOUND) {
                 throw new UserNotFoundException(userId, exception);
             }

                throw exception;
        }
    }

    @SuppressWarnings("PMD.LawOfDemeter")
    private Status.Code getStatusCode(final StatusRuntimeException exception) {
        final Status status = exception.getStatus();
        return status.getCode();
    }
}