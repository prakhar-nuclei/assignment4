package com.nuclei.userservice.security;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

class JwtEncoderConfigTest {

    private JwtEncoder jwtEncoder;

    @BeforeEach
    void setUp() throws Exception {
        final KeyPairGenerator keyPairGenerator =
                KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);

        final KeyPair keyPair =
                keyPairGenerator.generateKeyPair();

        final RSAPublicKey publicKey =
                (RSAPublicKey) keyPair.getPublic();

        final RSAPrivateKey privateKey =
                (RSAPrivateKey) keyPair.getPrivate();

        final JwtEncoderConfig config =
                new JwtEncoderConfig();

        jwtEncoder =
                config.jwtEncoder(publicKey, privateKey);
    }

    @Test
    void jwtEncoder_shouldGenerateJwt() {
        final JwtEncoderParameters parameters =
                createJwtParameters();

        final Jwt encodedJwt =
                jwtEncoder.encode(parameters);

        assertNotNull(encodedJwt);
        assertNotNull(encodedJwt.getTokenValue());
    }

    @Test
    void jwtEncoder_shouldGenerateJwtWithExpectedClaims() {
        final JwtEncoderParameters parameters =
                createJwtParameters();

        final Jwt encodedJwt =
                jwtEncoder.encode(parameters);

        assertEquals(
                "1",
                encodedJwt.getClaimAsString("sub")
        );

        assertEquals(
                "user-service",
                encodedJwt.getClaimAsString("iss")
        );
    }

    private JwtEncoderParameters createJwtParameters() {
        final Instant issuedAt = Instant.now();

        final JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .subject("1")
                        .issuer("user-service")
                        .issuedAt(issuedAt)
                        .expiresAt(issuedAt.plusSeconds(900))
                        .build();

        return JwtEncoderParameters.from(claims);
    }
}