package com.nuclei.userservice.service;

import com.nuclei.userservice.dto.UserCreateRequestDto;
import com.nuclei.userservice.dto.UserResponseDto;

public interface IUserService {

    UserResponseDto createUser(
            UserCreateRequestDto request,
            String idempotencyKey
    );

    UserResponseDto getUser(Long userId);
}
