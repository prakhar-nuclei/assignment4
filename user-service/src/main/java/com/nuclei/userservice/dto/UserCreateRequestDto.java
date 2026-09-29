package com.nuclei.userservice.dto;

public record UserCreateRequestDto(
        String name,
        String email,
        String password
) {
}