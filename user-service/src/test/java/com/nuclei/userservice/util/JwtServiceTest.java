package com.nuclei.userservice.util;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class JwtServiceTest {

    private JwtService jwtService;
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() throws Exception {
        final KeyPairGenerator keyPairGenerator =
                KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);

        final KeyPair keyPair = keyPairGenerator.generateKeyPair();

        final RSAKey rsaKey = new RSAKey.Builder(
                (RSAPublicKey) keyPair.getPublic())
                .privateKey(keyPair.getPrivate())
                .build();

        final NimbusJwtEncoder jwtEncoder =
                new NimbusJwtEncoder(
                        new ImmutableJWKSet<>(
                                new JWKSet(rsaKey)
                        )
                );

        jwtService = new JwtService(
                jwtEncoder,
                "user-service",
                900L
        );

        jwtDecoder = NimbusJwtDecoder
                .withPublicKey((RSAPublicKey) keyPair.getPublic())
                .build();
    }

    @Test
    void generateToken_shouldGenerateValidJwtWithExpectedClaims() {
        final Long userId = 1L;

        final String token = jwtService.generateToken(userId);

        assertNotNull(token);

        final Jwt decodedJwt = jwtDecoder.decode(token);

        assertEquals("1", decodedJwt.getSubject());
        assertEquals(
                "user-service",
                decodedJwt.getClaimAsString("iss")
        );
        assertNotNull(decodedJwt.getIssuedAt());
        assertNotNull(decodedJwt.getExpiresAt());
    }
}