package com.nuclei.userservice.util;

import com.nuclei.userservice.exception.EmailEncryptionException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import javax.crypto.IllegalBlockSizeException;
import org.cryptomator.siv.SivMode;
import org.cryptomator.siv.UnauthenticCiphertextException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EmailEncryptionUtil {

    private static final int AES_KEY_SIZE_BYTES = 32;

    private final SivMode sivMode;
    private final byte[] encryptionKey;
    private final byte[] macKey;

    public EmailEncryptionUtil(
            @Value("${email.encryption-key-path}") final String encryptionKeyPath,
            @Value("${email.mac-key-path}") final String macKeyPath) {

        this.encryptionKey = loadKey(
                encryptionKeyPath,
                "email.encryption-key"
        );

        this.macKey = loadKey(
                macKeyPath,
                "email.mac-key"
        );

        this.sivMode = new SivMode();
    }

    public String encrypt(final String email) {
        try {
            final byte[] plaintext =
                    email.getBytes(StandardCharsets.UTF_8);

            final byte[] encrypted = sivMode.encrypt(
                    encryptionKey,
                    macKey,
                    plaintext
            );

            return Base64.getEncoder()
                    .encodeToString(encrypted);

        } catch (IllegalArgumentException exception) {
            throw new EmailEncryptionException(
                    "Failed to encrypt email",
                    exception
            );
        }
    }

    private static byte[] loadKey(
            final String keyPath,
            final String propertyName) {

        try {
            final String encodedKey =
                    Files.readString(Path.of(keyPath)).trim();

            return decodeKey(encodedKey, propertyName);

        } catch (IOException exception) {
            throw new EmailEncryptionException(
                    "Failed to load " + propertyName,
                    exception
            );
        }
    }

    public String decrypt(final String encryptedEmail) {
        try {
            final byte[] ciphertext =
                    Base64.getDecoder()
                            .decode(encryptedEmail);

            final byte[] decrypted = sivMode.decrypt(
                    encryptionKey,
                    macKey,
                    ciphertext
            );

            return new String(
                    decrypted,
                    StandardCharsets.UTF_8
            );

        } catch (IllegalArgumentException
                 | UnauthenticCiphertextException
                 | IllegalBlockSizeException exception) {

            throw new EmailEncryptionException(
                    "Failed to decrypt email",
                    exception
            );
        }
    }

    private static byte[] decodeKey(
            final String encodedKey,
            final String propertyName) {

        try {
            final byte[] key =
                    Base64.getDecoder().decode(encodedKey);

            if (key.length != AES_KEY_SIZE_BYTES) {
                throw new EmailEncryptionException(
                        propertyName
                                + " must decode to exactly 32 bytes"
                );
            }

            return key;

        } catch (IllegalArgumentException exception) {
            throw new EmailEncryptionException(
                    "Invalid " + propertyName,
                    exception
            );
        }
    }
}