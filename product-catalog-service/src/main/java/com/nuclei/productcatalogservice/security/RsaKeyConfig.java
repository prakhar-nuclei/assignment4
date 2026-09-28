package com.nuclei.productcatalogservice.security;

import com.nuclei.productcatalogservice.exception.JwtKeyException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RsaKeyConfig {

    private static final String BEGIN_PUBLIC_KEY = "-----BEGIN PUBLIC KEY-----";
    private static final String END_PUBLIC_KEY = "-----END PUBLIC KEY-----";

    @Bean
    public RSAPublicKey publicKey(
            @Value("${jwt.public-key-path}") final String publicKeyPath) {

        try {
            final String publicKeyContent = Files.readString(Path.of(publicKeyPath))
                    .replace(BEGIN_PUBLIC_KEY, "")
                    .replace(END_PUBLIC_KEY, "")
                    .replaceAll("\\s+", "");

            final byte[] decodedKey =
                    Base64.getDecoder().decode(publicKeyContent);

            final X509EncodedKeySpec keySpec =
                    new X509EncodedKeySpec(decodedKey);

            final KeyFactory keyFactory = KeyFactory.getInstance("RSA");

            return (RSAPublicKey) keyFactory.generatePublic(keySpec);
        } catch (IOException | GeneralSecurityException | IllegalArgumentException exception) {
            throw new JwtKeyException(
                    "Failed to load RSA public key",
                    exception
            );
        }
    }
}