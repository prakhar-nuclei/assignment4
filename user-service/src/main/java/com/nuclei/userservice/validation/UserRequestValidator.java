package com.nuclei.userservice.validation;

import com.nuclei.userservice.exception.InvalidUserRequestException;
import org.springframework.stereotype.Component;

@Component
public class UserRequestValidator {

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 72;

    private static final String EMAIL_REGEX =
            "^[A-Za-z0-9]+([._%+-][A-Za-z0-9]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+$";

    public void validateCreateUser(
            final String name,
            final String email,
            final String password) {

        if (name == null || name.isBlank()) {
            throw new InvalidUserRequestException("Name is required");
        }

        validateEmail(email);
        validatePassword(password);
    }

    public void validateAuthenticateUser(
            final String email,
            final String password) {

        validateEmail(email);
        validatePassword(password);
    }

    public void validateGetUser(final Long userId) {

        if (userId == null || userId <= 0) {
            throw new InvalidUserRequestException(
                    "User ID must be greater than zero"
            );
        }
    }

    private void validateEmail(final String email) {

        if (email == null || email.isBlank()) {
            throw new InvalidUserRequestException("Email is required");
        }

        if (!email.matches(EMAIL_REGEX)) {
            throw new InvalidUserRequestException("Invalid email format");
        }
    }

    private void validatePassword(final String password) {

        if (password == null || password.isBlank()) {
            throw new InvalidUserRequestException("Password is required");
        }

        if (password.length() < MIN_PASSWORD_LENGTH
                || password.length() > MAX_PASSWORD_LENGTH) {

            throw new InvalidUserRequestException(
                    "Password must be between "
                            + MIN_PASSWORD_LENGTH
                            + " and "
                            + MAX_PASSWORD_LENGTH
                            + " characters"
            );
        }
    }
}