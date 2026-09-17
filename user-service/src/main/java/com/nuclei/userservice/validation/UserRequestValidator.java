package com.nuclei.userservice.validation;

import com.nuclei.userservice.exception.InvalidUserRequestException;
import org.springframework.stereotype.Component;

@Component
public class UserRequestValidator {

    public void validateCreateUser(
            final String name,
            final String email,
            final String password) {

        if (name == null || name.isBlank()) {
            throw new InvalidUserRequestException("Name is required");
        }

        if (email == null || email.isBlank()) {
            throw new InvalidUserRequestException("Email is required");
        }

        if (password == null || password.isBlank()) {
            throw new InvalidUserRequestException("Password is required");
        }
    }

    public void validateAuthenticateUser(
            final String email,
            final String password) {

        if (email == null || email.isBlank()) {
            throw new InvalidUserRequestException("Email is required");
        }

        if (password == null || password.isBlank()) {
            throw new InvalidUserRequestException("Password is required");
        }
    }

    public void validateGetUser(final Long userId) {

        if (userId == null || userId <= 0) {
            throw new InvalidUserRequestException(
                    "User ID must be greater than zero"
            );
        }
    }
}