package com.nuclei.userservice.grpc.mapper;

import com.nuclei.user.proto.AuthenticateUserResponse;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationResponseMapper {

    public AuthenticateUserResponse toGrpcResponse(
            final String accessToken) {

        return AuthenticateUserResponse.newBuilder()
                .setAccessToken(accessToken)
                .build();
    }
}