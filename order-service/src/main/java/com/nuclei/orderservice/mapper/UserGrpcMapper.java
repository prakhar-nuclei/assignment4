package com.nuclei.orderservice.mapper;

import com.nuclei.orderservice.dtos.UserResponseDto;
import com.nuclei.user.proto.UserResponse;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@NoArgsConstructor
public class UserGrpcMapper {

    public UserResponseDto toUserResponseDto(final UserResponse response) {
        return new UserResponseDto(
                response.getUserId(),
                response.getName(),
                response.getEmail()
        );
    }
}