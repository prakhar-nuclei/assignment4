package com.nuclei.userservice.service;

import com.nuclei.userservice.dto.UserResponseDto;
import com.nuclei.userservice.entity.User;

public interface IUserService {

    UserResponseDto createUser(
            String name,
            String email,
            String password
    );

    UserResponseDto getUser(Long userId);
}
