package com.nuclei.userservice.service;

import com.nuclei.userservice.dto.UserResponseDto;

public interface IUserService {

    UserResponseDto createUser(
            String name,
            String email,
            String password,
            String idempotencyKey
    );

    UserResponseDto getUser(Long userId);
}
