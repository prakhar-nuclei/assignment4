package com.nuclei.userservice.grpc.mapper;

import com.nuclei.user.proto.UserResponse;
import com.nuclei.userservice.dto.UserResponseDto;
import org.springframework.stereotype.Component;

@Component
public class UserResponseMapper {

    public UserResponse toGrpcResponse(
            final UserResponseDto user) {

        return UserResponse.newBuilder()
                .setUserId(user.userId())
                .setName(user.name())
                .setEmail(user.email())
                .build();
    }
}