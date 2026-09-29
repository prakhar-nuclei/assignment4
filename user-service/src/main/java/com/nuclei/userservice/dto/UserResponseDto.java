package com.nuclei.userservice.dto;

public record UserResponseDto(
        Long userId,
        String name,
        String email
) {
}