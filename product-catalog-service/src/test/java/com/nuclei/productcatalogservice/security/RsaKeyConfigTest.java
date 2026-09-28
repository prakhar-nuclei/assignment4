package com.nuclei.productcatalogservice.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.nuclei.productcatalogservice.exception.JwtKeyException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RsaKeyConfigTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldLoadValidRsaPublicKey() throws Exception {
        Path publicKeyFile = createPublicKeyFile();

        RsaKeyConfig config = new RsaKeyConfig();

        RSAPublicKey publicKey = assertDoesNotThrow(
                () -> config.publicKey(publicKeyFile.toString())
        );

        assertInstanceOf(RSAPublicKey.class, publicKey);
    }

    @Test
    void shouldThrowJwtKeyExceptionWhenPublicKeyFileDoesNotExist() {
        RsaKeyConfig config = new RsaKeyConfig();

        Path missingFile = temporaryDirectory.resolve("missing-public-key.pem");

        assertThrows(
                JwtKeyException.class,
                () -> config.publicKey(missingFile.toString())
        );
    }

    @Test
    void shouldThrowJwtKeyExceptionWhenPublicKeyContentIsInvalid()
            throws Exception {

        Path invalidKeyFile =
                temporaryDirectory.resolve("invalid-public-key.pem");

        Files.writeString(
                invalidKeyFile,
                """
                -----BEGIN PUBLIC KEY-----
                invalid-key-content
                -----END PUBLIC KEY-----
                """,
                StandardCharsets.US_ASCII
        );

        RsaKeyConfig config = new RsaKeyConfig();

        assertThrows(
                JwtKeyException.class,
                () -> config.publicKey(invalidKeyFile.toString())
        );
    }

    private Path createPublicKeyFile() throws Exception {
        KeyPairGenerator keyPairGenerator =
                KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);

        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        String encodedPublicKey = Base64.getEncoder()
                .encodeToString(keyPair.getPublic().getEncoded());

        String pemContent = """
                -----BEGIN PUBLIC KEY-----
                %s
                -----END PUBLIC KEY-----
                """.formatted(encodedPublicKey);

        Path publicKeyFile =
                temporaryDirectory.resolve("public-key.pem");

        Files.writeString(
                publicKeyFile,
                pemContent,
                StandardCharsets.US_ASCII
        );

        return publicKeyFile;
    }
}