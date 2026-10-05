package com.nuclei.orderservice.dtos;

public record UserResponseDto(
        Long userId,
        String name,
        String email) {
}