package com.nuclei.userservice.security;

import com.nuclei.userservice.exception.JwtKeyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

class RsaKeyConfigTest {

    @TempDir
    private Path tempDirectory;

    @Test
    void privateKey_shouldLoadFromPemFile() throws Exception {
        final KeyPair keyPair = generateKeyPair();

        final Path privateKeyPath =
                tempDirectory.resolve("private-key.pem");

        writePemFile(privateKeyPath, keyPair);

        final RsaKeyConfig config =
                new RsaKeyConfig();

        ReflectionTestUtils.setField(
                config,
                "privateKeyPath",
                privateKeyPath.toString()
        );

        final RSAPrivateKey privateKey =
                config.privateKey();

        assertInstanceOf(RSAPrivateKey.class, privateKey);
    }

    @Test
    void publicKey_shouldLoadFromPemFile() throws Exception {
        final KeyPair keyPair = generateKeyPair();

        final Path publicKeyPath =
                tempDirectory.resolve("public-key.pem");

        writePemFile(publicKeyPath, keyPair.getPublic());

        final RsaKeyConfig config =
                new RsaKeyConfig();

        ReflectionTestUtils.setField(
                config,
                "publicKeyPath",
                publicKeyPath.toString()
        );

        final RSAPublicKey publicKey =
                config.publicKey();

        assertInstanceOf(RSAPublicKey.class, publicKey);
    }

    @Test
    void privateKey_shouldThrowJwtKeyExceptionWhenFileDoesNotExist() {
        final Path privateKeyPath =
                tempDirectory.resolve("missing-private-key.pem");

        final RsaKeyConfig config =
                new RsaKeyConfig();

        ReflectionTestUtils.setField(
                config,
                "privateKeyPath",
                privateKeyPath.toString()
        );

        assertThrows(
                JwtKeyException.class,
                config::privateKey
        );
    }

    private KeyPair generateKeyPair() throws Exception {
        final KeyPairGenerator keyPairGenerator =
                KeyPairGenerator.getInstance("RSA");

        keyPairGenerator.initialize(2048);

        return keyPairGenerator.generateKeyPair();
    }

    private void writePemFile(
            final Path path,
            final Object object) throws Exception {

        try (JcaPEMWriter writer =
                     new JcaPEMWriter(Files.newBufferedWriter(path))) {

            writer.writeObject(object);
        }
    }
}