package com.srecatalog.harness;

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
        return token -> Jwt.withTokenValue(token)
                .header("alg", "none")
                .subject("admin-001")
                .claim("preferred_username", "admin-001")
                .claim("title", "Platform Admin")
                .claim("roles", List.of("PLATFORM_ADMIN"))
                .claim("groups", List.of())
                .claim("covering_lobs", List.of("FICC"))
                .claim("lob", "FICC")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }
}
