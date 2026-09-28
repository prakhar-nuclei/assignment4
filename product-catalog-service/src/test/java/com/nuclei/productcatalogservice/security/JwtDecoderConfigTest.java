package com.nuclei.productcatalogservice.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.UUID;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class JwtDecoderConfigTest {

    private static final String ISSUER = "http://localhost:6565";
    private static final String WRONG_ISSUER = "http://wrong-issuer";

    private JwtDecoder jwtDecoder;
    private JwtEncoder jwtEncoder;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator keyPairGenerator =
                KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);

        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        RSAPublicKey publicKey =
                (RSAPublicKey) keyPair.getPublic();

        RSAPrivateKey privateKey =
                (RSAPrivateKey) keyPair.getPrivate();

        JwtDecoderConfig config = new JwtDecoderConfig();

        jwtDecoder = config.jwtDecoder(publicKey, ISSUER);

        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(UUID.randomUUID().toString())
                .build();

        jwtEncoder = new NimbusJwtEncoder(
                new ImmutableJWKSet<>(
                        new JWKSet(rsaKey)
                )
        );
    }

    @Test
    void shouldDecodeValidJwt() {
        String token = createToken(
                jwtEncoder,
                ISSUER,
                Instant.now().plusSeconds(300)
        );

        Jwt jwt = assertDoesNotThrow(
                () -> jwtDecoder.decode(token)
        );

        assertEquals(ISSUER, jwt.getIssuer().toString());
    }

    @Test
    void shouldRejectJwtWithWrongIssuer() {
        String token = createToken(
                jwtEncoder,
                WRONG_ISSUER,
                Instant.now().plusSeconds(300)
        );

        assertThrows(
                JwtException.class,
                () -> jwtDecoder.decode(token)
        );
    }

    @Test
    void shouldRejectExpiredJwt() {
        Instant issuedAt = Instant.now().minusSeconds(600);
        Instant expiresAt = Instant.now().minusSeconds(300);

        String token = createToken(
                jwtEncoder,
                ISSUER,
                issuedAt,
                expiresAt
        );

        assertThrows(
                JwtException.class,
                () -> jwtDecoder.decode(token)
        );
    }

    @Test
    void shouldRejectJwtWithInvalidSignature() throws Exception {
        KeyPairGenerator keyPairGenerator =
                KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);

        KeyPair differentKeyPair = keyPairGenerator.generateKeyPair();

        RSAKey differentRsaKey = new RSAKey.Builder(
                (RSAPublicKey) differentKeyPair.getPublic())
                .privateKey(
                        (RSAPrivateKey) differentKeyPair.getPrivate()
                )
                .keyID(UUID.randomUUID().toString())
                .build();

        JwtEncoder differentEncoder = new NimbusJwtEncoder(
                new ImmutableJWKSet<>(
                        new JWKSet(differentRsaKey)
                )
        );

        String token = createToken(
                differentEncoder,
                ISSUER,
                Instant.now().plusSeconds(300)
        );

        assertThrows(
                JwtException.class,
                () -> jwtDecoder.decode(token)
        );
    }

    private String createToken(
            JwtEncoder encoder,
            String issuer,
            Instant expiresAt) {

        return createToken(
                encoder,
                issuer,
                Instant.now(),
                expiresAt
        );
    }

    private String createToken(
            JwtEncoder encoder,
            String issuer,
            Instant issuedAt,
            Instant expiresAt) {

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("test-user")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();

        JwsHeader header = JwsHeader.with(() -> "RS256").build();

        return encoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();
    }
}