package com.nuclei.orderservice.dtos;

public record UserDetailsDto(
        Long userId,
        String name,
        String email) {
}