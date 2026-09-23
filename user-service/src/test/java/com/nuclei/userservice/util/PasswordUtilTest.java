package com.nuclei.userservice.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordUtilTest {

    private PasswordUtil passwordUtil;

    @BeforeEach
    void setUp() {
        final PasswordEncoder passwordEncoder =
                new BCryptPasswordEncoder();

        passwordUtil =
                new PasswordUtil(passwordEncoder);
    }

    @Test
    void hash_shouldCreateValidPasswordHash() {
        final String rawPassword = "password123";

        final String hashedPassword =
                passwordUtil.hash(rawPassword);

        assertNotNull(hashedPassword);
        assertTrue(
                passwordUtil.matches(
                        rawPassword,
                        hashedPassword
                )
        );
    }

    @Test
    void matches_shouldReturnFalseForIncorrectPassword() {
        final String rawPassword = "password123";

        final String hashedPassword =
                passwordUtil.hash(rawPassword);

        assertFalse(
                passwordUtil.matches(
                        "wrong-password",
                        hashedPassword
                )
        );
    }
}