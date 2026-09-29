package com.nuclei.userservice.mapper;

import com.nuclei.userservice.dto.UserResponseDto;
import com.nuclei.userservice.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponseDto toResponseDto(final User user) {
        return new UserResponseDto(
                user.getId(),
                user.getName(),
                user.getEmail()
        );
    }

    public User toEntity(
            final String name,
            final String email,
            final String rawPassword) {

        final User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setRawPassword(rawPassword);

        return user;
    }
}