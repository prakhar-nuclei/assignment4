package com.nuclei.userservice.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Component;

@Component
public class RequestHashUtil {

    public String generateHash(
            final String name,
            final String normalizedEmail) {

        final String requestData =
                name + "|" + normalizedEmail;

        try {
            final MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            final byte[] hash =
                    digest.digest(
                            requestData.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            final StringBuilder result =
                    new StringBuilder();

            for (final byte value : hash) {
                result.append(
                        String.format("%02x", value)
                );
            }

            return result.toString();

        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is not available",
                    exception
            );
        }
    }
}