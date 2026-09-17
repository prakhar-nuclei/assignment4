package com.nuclei.userservice.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final long expirationSeconds;

    public JwtService(
            final JwtEncoder jwtEncoder,
            @Value("${jwt.issuer}") final String issuer,
            @Value("${jwt.expiration-seconds}") final long expirationSeconds) {

        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
        this.expirationSeconds = expirationSeconds;
    }

    public String generateToken(final Long userId) {

        final Instant now = Instant.now();

        final JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(expirationSeconds))
                .build();

        return jwtEncoder
                .encode(JwtEncoderParameters.from(claims)) // claims ko parameter mai wrap
                .getTokenValue();
    }
}