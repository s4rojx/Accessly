package com.assessly.auth;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtKeyConfiguration {
    @Bean
    RSAPrivateKey jwtPrivateKey(JwtProperties properties) {
        try {
            byte[] encoded = Base64.getDecoder().decode(required(properties.getPrivateKeyBase64(), "ASSESSLY_JWT_PRIVATE_KEY_BASE64"));
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid JWT private key configuration", exception);
        }
    }

    @Bean
    RSAPublicKey jwtPublicKey(JwtProperties properties) {
        try {
            byte[] encoded = Base64.getDecoder().decode(required(properties.getPublicKeyBase64(), "ASSESSLY_JWT_PUBLIC_KEY_BASE64"));
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(encoded));
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid JWT public key configuration", exception);
        }
    }

    @Bean
    JwtEncoder jwtEncoder(RSAPrivateKey privateKey, RSAPublicKey publicKey) {
        RSAKey rsaKey = new RSAKey.Builder(publicKey).privateKey(privateKey).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(rsaKey)));
    }

    @Bean
    JwtDecoder jwtDecoder(RSAPublicKey publicKey) {
        return NimbusJwtDecoder.withPublicKey(publicKey).build();
    }

    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    private static String required(String value, String variable) {
        if (value == null || value.isBlank()) throw new IllegalStateException(variable + " must be configured");
        return value;
    }
}
