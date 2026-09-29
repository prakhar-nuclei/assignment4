package com.nuclei.userservice.util;

import com.nuclei.userservice.exception.EmailEncryptionException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EmailEncryptionUtilTest {

    @TempDir
    Path tempDir;

    private EmailEncryptionUtil emailEncryptionUtil;

    @BeforeEach
    void setUp() throws Exception {
        final String encryptionKey = Base64.getEncoder()
                .encodeToString(new byte[32]);

        final String macKey = Base64.getEncoder()
                .encodeToString(new byte[32]);

        final Path encryptionKeyPath =
                tempDir.resolve("encryption.key");

        final Path macKeyPath =
                tempDir.resolve("mac.key");

        Files.writeString(encryptionKeyPath, encryptionKey);
        Files.writeString(macKeyPath, macKey);

        emailEncryptionUtil =
                new EmailEncryptionUtil(
                        encryptionKeyPath.toString(),
                        macKeyPath.toString()
                );
    }

    @Test
    void encryptAndDecrypt_shouldReturnOriginalEmail() {
        final String email = "test@email.com";

        final String encryptedEmail =
                emailEncryptionUtil.encrypt(email);

        final String decryptedEmail =
                emailEncryptionUtil.decrypt(encryptedEmail);

        assertNotEquals(email, encryptedEmail);
        assertEquals(email, decryptedEmail);
    }

    @Test
    void encrypt_shouldReturnSameCiphertextForSameEmail() {
        final String email = "test@email.com";

        final String firstEncryptedEmail =
                emailEncryptionUtil.encrypt(email);

        final String secondEncryptedEmail =
                emailEncryptionUtil.encrypt(email);

        assertEquals(
                firstEncryptedEmail,
                secondEncryptedEmail
        );
    }

    @Test
    void decrypt_shouldThrowExceptionForInvalidCiphertext() {
        final String invalidCiphertext = "invalid-ciphertext";

        assertThrows(
                EmailEncryptionException.class,
                () -> emailEncryptionUtil.decrypt(
                        invalidCiphertext
                )
        );
    }

    @Test
    void constructor_shouldThrowExceptionForInvalidEncryptionKey()
            throws Exception {
        final String invalidEncryptionKey =
                Base64.getEncoder()
                        .encodeToString(new byte[16]);

        final String validMacKey =
                Base64.getEncoder()
                        .encodeToString(new byte[32]);

        final Path invalidEncryptionKeyPath =
                tempDir.resolve("invalid-encryption.key");

        final Path validMacKeyPath =
                tempDir.resolve("valid-mac.key");

        Files.writeString(
                invalidEncryptionKeyPath,
                invalidEncryptionKey
        );

        Files.writeString(
                validMacKeyPath,
                validMacKey
        );

        assertThrows(
                EmailEncryptionException.class,
                () -> new EmailEncryptionUtil(
                        invalidEncryptionKeyPath.toString(),
                        validMacKeyPath.toString()
                )
        );
    }
}

