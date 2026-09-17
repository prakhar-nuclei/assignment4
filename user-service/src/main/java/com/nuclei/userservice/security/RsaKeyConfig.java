package com.nuclei.userservice.security;

import com.nuclei.userservice.exception.JwtKeyException;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@Configuration
public class RsaKeyConfig {

    @Value("${jwt.private-key-path}")
    private String privateKeyPath;

    @Value("${jwt.public-key-path}")
    private String publicKeyPath;
    @Bean
    public RSAPrivateKey privateKey() {
        try (Reader reader = Files.newBufferedReader(Path.of(privateKeyPath));
             PEMParser parser = new PEMParser(reader)) {

            PEMKeyPair keyPair = (PEMKeyPair) parser.readObject(); // storing the pem key in pair

            return (RSAPrivateKey) new JcaPEMKeyConverter() // bouncy castle to java security key
                    .getKeyPair(keyPair)
                    .getPrivate();

        } catch (Exception e) {
            throw new JwtKeyException("Failed to load RSA private key", e);
        }
    }

    @Bean
    public RSAPublicKey publicKey() {
        try (Reader reader = Files.newBufferedReader(Path.of(publicKeyPath));
             PEMParser parser = new PEMParser(reader)) {

            var publicKeyInfo =
                    (org.bouncycastle.asn1.x509.SubjectPublicKeyInfo) parser.readObject();

            return (RSAPublicKey) new JcaPEMKeyConverter()
                    .getPublicKey(publicKeyInfo);

        } catch (Exception e) {
            throw new JwtKeyException("Failed to load RSA public key", e);
        }
    }
}