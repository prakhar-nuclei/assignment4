package com.nuclei.userservice.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class EmailUtilTest {

    private final EmailUtil emailUtil = new EmailUtil();

    @Test
    void normalize_shouldTrimAndConvertEmailToLowercase() {
        final String email = "  TEST@Email.COM  ";

        final String result =
                emailUtil.normalize(email);

        assertEquals("test@email.com", result);
    }

    @Test
    void normalize_shouldReturnSameEmailWhenAlreadyNormalized() {
        final String email = "test@email.com";

        final String result =
                emailUtil.normalize(email);

        assertEquals(email, result);
    }
}