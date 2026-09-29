package com.nuclei.userservice.security;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;

class JwtDecoderConfigTest {

    @Test
    void jwtDecoder_shouldDecodeValidRs256Token() throws Exception {
        final KeyPairGenerator keyPairGenerator =
                KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);

        final KeyPair keyPair =
                keyPairGenerator.generateKeyPair();

        final RSAPublicKey publicKey =
                (RSAPublicKey) keyPair.getPublic();

        final RSAKey rsaKey =
                new RSAKey.Builder(publicKey)
                        .privateKey(keyPair.getPrivate())
                        .build();

        final JwtEncoder jwtEncoder =
                new NimbusJwtEncoder(
                        new ImmutableJWKSet<>(
                                new JWKSet(rsaKey)
                        )
                );

        final JwtDecoderConfig config =
                new JwtDecoderConfig();

        final JwtDecoder jwtDecoder =
                config.jwtDecoder(publicKey, "user-service");

        final Instant issuedAt = Instant.now();

        final JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .subject("1")
                        .issuer("user-service")
                        .issuedAt(issuedAt)
                        .expiresAt(issuedAt.plusSeconds(900))
                        .build();

        final JwtEncoderParameters parameters =
                JwtEncoderParameters.from(
                        JwsHeader.with(SignatureAlgorithm.RS256)
                                .build(),
                        claims
                );

        final Jwt encodedJwt =
                jwtEncoder.encode(parameters);

        final Jwt decodedJwt =
                jwtDecoder.decode(encodedJwt.getTokenValue());

        assertNotNull(decodedJwt);
    }

    @Test
    void jwtDecoder_shouldRejectTokenWithWrongIssuer() throws Exception {
        final KeyPairGenerator keyPairGenerator =
                KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);

        final KeyPair keyPair =
                keyPairGenerator.generateKeyPair();

        final RSAPublicKey publicKey =
                (RSAPublicKey) keyPair.getPublic();

        final RSAKey rsaKey =
                new RSAKey.Builder(publicKey)
                        .privateKey(keyPair.getPrivate())
                        .build();

        final JwtEncoder jwtEncoder =
                new NimbusJwtEncoder(
                        new ImmutableJWKSet<>(
                                new JWKSet(rsaKey)
                        )
                );

        final JwtDecoderConfig config =
                new JwtDecoderConfig();

        final JwtDecoder jwtDecoder =
                config.jwtDecoder(publicKey, "user-service");

        final Instant issuedAt = Instant.now();

        final JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .subject("1")
                        .issuer("wrong-service")
                        .issuedAt(issuedAt)
                        .expiresAt(issuedAt.plusSeconds(900))
                        .build();

        final JwtEncoderParameters parameters =
                JwtEncoderParameters.from(
                        JwsHeader.with(SignatureAlgorithm.RS256)
                                .build(),
                        claims
                );

        final Jwt encodedJwt =
                jwtEncoder.encode(parameters);

        assertThrows(
                JwtException.class,
                () -> jwtDecoder.decode(encodedJwt.getTokenValue())
        );
    }
}