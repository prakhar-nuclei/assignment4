package com.nuclei.userservice.security;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@TestConfiguration(proxyBeanMethods = false)
public class GrpcJwtTestConfig {

    private static final String TEST_ISSUER = "user-service";

    private static final String TEST_PRIVATE_KEY = """
            -----BEGIN PRIVATE KEY-----
            MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCzIGOjKGMQlyld
            47f0z6bapQiDJAOGW5my4A4fTjR1ueaBv5p+H9m1hVSz/i/UVL2/p0v+FMemMTZ4
            xIeMAW02FOt2oQfrUwGyN5q9vEEm0EK3ltQxxhU1b65YN0/KaHaMzQhzd5yWi2AI
            MBhR6ae/vkmXhg4iHhJdpxqe+4FrUNZh+0MSorm/4d2A80IFPA1nzZ9ZislG7Okp
            fceXq5j+tyx4mHw74fhuc814qN2kaCGR2T4H4cVsvef8sTQAY6t2DFmvGSYC+3En
            hh1v4ghFsmmqY7r8zxlsXyfMZpBX+OizS4T8v7rqs9B7eIn3H4HPVWH2VnfrbX2L
            VbOur51FAgMBAAECggEAGaPoP0GXtJc+p57iBRv4MVHwPv1Xt9LC1HT38Ik8PyHG
            Ggp4LdeXTUFxl3YqGIWsx5NF8G+/CE4BExZmpKbLoDMsZUW1oXy3QHBvKPuag9jJ
            xAfGBf3pqUV8OkpolHuRhKQQsp6Lfo0iyOlBgfAlP/AYDqV0vnN5g4hY6irsIpUp
            EN6XIEc71ib7C069yYncBIl3wx5Pn8iy4JWFb5QRxdvQKbpSCrxOdHBYWnQGRFaV
            FSULgh4OMl/TPMQONLtcc9lhchKZt3bzwlGZL3LiNEDm7xD78MCfO4LZ//n2WFbh
            6rSbdCZBa17hQYNISXtn3cWfXVVGJYr4iVlJsnV3PQKBgQDsPHquK5ot+NWwpoBJ
            BlchWvtAz9J2zyH72hEVeR08cHwKjKGNJvqcTbDpFHqkMFpHOc/bqHugDulg5CN6
            A5y9AzQ21qxNtyW724GTTu8V4JDhWH/ftF09l+6dVfbhyvP3E26K9TYZrUSwkGZV
            lXPk/CT52kcJQhqKK9A4evS7vwKBgQDCHMdzqvXElUbsamfPC2b+mI53MQA1e/WI
            4NZ+1xlj8/L6sUCYABKUrZs4JbuJGcIdJ8IyOuXgzegF1o9yTfJs5qoJX0mC2ho5
            q2I8p0O7fETy4pRj5mEagm7qt2K18CCiqSeNwKXwxVp6GSzYJoQIo8Y/einOzmgm
            9+G+qvq3+wKBgFV1W3v+UfwndEY/NVE1dfefDh1YEO7h541QSbXW5niiyHyYeyiw
            3SAGCuWGfwc+Zqo1g7gWrwIr4skmPp85B05pBk6AQjyq8H+Q3MMJXvwvDLo4Zanr
            eX96ottP6cusu6y53tdt9XA8egjVc++p7Q79F/M6v0/eFfO9/ckw0a/JAoGAKp2O
            3EIIOuRa8cRajgX+0p5DiL9lQFi6ixZxgN9QX7VK/uazO6uoaKPElAsUpQDWsPft
            loF/vl72NXHphDrAok/xvKUCyot/fnG124MNR7f8myZLVbNaViuUlks0jKTvRUbF
            vEUzKSzqtCuawMmT6yGpgH4dd52xF/0LvoRjDskCgYEAwqhd65GHYKtyFXExC+cB
            jVr5dxRjSoSBqYUKk/YhkUDHzlzw2eAhpU8SCU6xyJIQiIw4Uw3W2HDzWZTXrSwb
            U/lah6iCYmcJOJ0sOOIr3SSnPP4ybGnFsfH3s/5FfUuIOaLgXShKKZov3GXI9qwK
            VFemW1y/lTvLZzize+gAVik=
            -----END PRIVATE KEY-----
            """;

    private static final String TEST_PUBLIC_KEY = """
            -----BEGIN PUBLIC KEY-----
            MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAsyBjoyhjEJcpXeO39M+m
            2qUIgyQDhluZsuAOH040dbnmgb+afh/ZtYVUs/4v1FS9v6dL/hTHpjE2eMSHjAFt
            NhTrdqEH61MBsjeavbxBJtBCt5bUMcYVNW+uWDdPymh2jM0Ic3eclotgCDAYUemn
            v75Jl4YOIh4SXacanvuBa1DWYftDEqK5v+HdgPNCBTwNZ82fWYrJRuzpKX3Hl6uY
            /rcseJh8O+H4bnPNeKjdpGghkdk+B+HFbL3n/LE0AGOrdgxZrxkmAvtxJ4Ydb+II
            RbJpqmO6/M8ZbF8nzGaQV/jos0uE/L+66rPQe3iJ9x+Bz1Vh9lZ36219i1Wzrq+d
            RQIDAQAB
            -----END PUBLIC KEY-----
            """;

    @Bean
    public RSAPrivateKey privateKey() throws Exception {
        final String encodedKey = TEST_PRIVATE_KEY
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        final byte[] decodedKey =
                Base64.getDecoder().decode(encodedKey);

        final PKCS8EncodedKeySpec keySpec =
                new PKCS8EncodedKeySpec(decodedKey);

        return (RSAPrivateKey) KeyFactory
                .getInstance("RSA")
                .generatePrivate(keySpec);
    }

    @Bean
    public RSAPublicKey publicKey() throws Exception {
        final String encodedKey = TEST_PUBLIC_KEY
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        final byte[] decodedKey =
                Base64.getDecoder().decode(encodedKey);

        final X509EncodedKeySpec keySpec =
                new X509EncodedKeySpec(decodedKey);

        return (RSAPublicKey) KeyFactory
                .getInstance("RSA")
                .generatePublic(keySpec);
    }

    @Bean
    public JwtEncoder jwtEncoder(
            final RSAPublicKey publicKey,
            final RSAPrivateKey privateKey) {

        final RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .build();

        final JWKSet jwkSet = new JWKSet(rsaKey);

        final JWKSource<com.nimbusds.jose.proc.SecurityContext>
                jwkSource =
                new ImmutableJWKSet<>(jwkSet);

        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    public JwtDecoder jwtDecoder(
            final RSAPublicKey publicKey) {

        final NimbusJwtDecoder jwtDecoder =
                NimbusJwtDecoder.withPublicKey(publicKey)
                        .build();

        jwtDecoder.setJwtValidator(
                JwtValidators.createDefaultWithIssuer(TEST_ISSUER)
        );

        return jwtDecoder;
    }
}
