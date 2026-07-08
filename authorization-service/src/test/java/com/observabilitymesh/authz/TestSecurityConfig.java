package com.observabilitymesh.authz;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.List;

@TestConfiguration
public class TestSecurityConfig {

    @Bean
    JwtDecoder jwtDecoder() {
        return token -> {
            if (token.contains("svc-instruction")) {
                return serviceJwt("svc-instruction");
            }
            if (token.contains("svc-payment")) {
                return serviceJwt("svc-payment");
            }
            if (token.contains("unauthorized")) {
                return serviceJwt("svc-other");
            }
            return adminJwt();
        };
    }

    static Jwt serviceJwt(String userId) {
        return Jwt.withTokenValue(userId)
                .header("alg", "none")
                .subject(userId)
                .claim("preferred_username", userId)
                .claim("title", "Service")
                .claim("roles", List.of("SERVICE"))
                .claim("groups", List.of())
                .claim("covering_lobs", List.of())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    static Jwt adminJwt() {
        return Jwt.withTokenValue("admin-token")
                .header("alg", "none")
                .subject("admin-001")
                .claim("preferred_username", "admin-001")
                .claim("title", "Platform Admin")
                .claim("roles", List.of("PLATFORM_ADMIN"))
                .claim("groups", List.of("ADMIN"))
                .claim("covering_lobs", List.of("FICC"))
                .claim("lob", "FICC")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }
}
