package com.srecatalog.auth;

import com.srecatalog.common.model.Subject;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequestSubjectResolverTest {

    private final SubjectExtractor extractor = new SubjectExtractor(
            new com.fasterxml.jackson.databind.ObjectMapper());
    private RequestSubjectResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new RequestSubjectResolver(extractor);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolveActorUsesOnBehalfOfHeader() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString("""
                {"sub":"mo-100","title":"Analyst","roles":["INSTRUCTION_CREATOR"]}
                """.getBytes());
        when(request.getHeader(RequestSubjectResolver.OBO_HEADER)).thenReturn("Bearer header." + payload + ".sig");

        Subject subject = resolver.resolveActor(request);

        assertThat(subject.userId()).isEqualTo("mo-100");
    }

    @Test
    void resolveActorUsesAuthenticationWhenOboMissing() {
        Jwt jwt = jwt("mo-100", "Analyst");
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(RequestSubjectResolver.OBO_HEADER)).thenReturn(null);

        Subject subject = resolver.resolveActor(request);

        assertThat(subject.userId()).isEqualTo("mo-100");
    }

    @Test
    void bearerTokenPrefersAuthorizationHeader() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer header-token");

        assertThat(resolver.bearerToken(request)).isEqualTo("header-token");
    }

    @Test
    void bearerTokenFallsBackToSecurityContext() {
        Jwt jwt = jwt("mo-100", "Analyst");
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn(null);

        assertThat(resolver.bearerToken(request)).isEqualTo("token-value");
    }

    @Test
    void sessionIdReturnsEmptyWhenMissing() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(RequestSubjectResolver.SESSION_HEADER)).thenReturn(null);
        assertThat(resolver.sessionId(request)).isEmpty();
    }

    @Test
    void currentJwtRequiresAuthentication() {
        assertThatThrownBy(() -> resolver.currentJwt())
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
                .isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void resolveCallerUsesJwtAuthentication() {
        Jwt jwt = jwt("admin-001", "Managing Director");
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        HttpServletRequest request = mock(HttpServletRequest.class);

        assertThat(resolver.resolveCaller(request).userId()).isEqualTo("admin-001");
    }

    @Test
    void bearerTokenReturnsNullWhenMissing() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn(null);
        assertThat(resolver.bearerToken(request)).isNull();
    }

    @Test
    void bearerTokenReturnsRawAuthorizationWithoutBearerPrefix() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("legacy-token");
        assertThat(resolver.bearerToken(request)).isNull();
    }

    @Test
    void resolveActorStripsBearerPrefixFromOboToken() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString("""
                {"sub":"mo-100","title":"Analyst","roles":["INSTRUCTION_CREATOR"]}
                """.getBytes());
        when(request.getHeader(RequestSubjectResolver.OBO_HEADER)).thenReturn("header." + payload + ".sig");

        Subject subject = resolver.resolveActor(request);

        assertThat(subject.userId()).isEqualTo("mo-100");
    }

    @Test
    void resolveActorRejectsBlankOboHeader() {
        Jwt jwt = jwt("mo-100", "Analyst");
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(RequestSubjectResolver.OBO_HEADER)).thenReturn("   ");

        assertThat(resolver.resolveActor(request).userId()).isEqualTo("mo-100");
    }

    @Test
    void subjectFromAuthenticationFailsWhenMissing() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(RequestSubjectResolver.OBO_HEADER)).thenReturn(null);

        assertThatThrownBy(() -> resolver.resolveActor(request))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void sessionIdReturnsHeaderValue() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(RequestSubjectResolver.SESSION_HEADER)).thenReturn("sess-123");
        assertThat(resolver.sessionId(request)).isEqualTo("sess-123");
    }

    @Test
    void currentJwtReturnsTokenWhenAuthenticated() {
        Jwt jwt = jwt("mo-100", "Analyst");
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        assertThat(resolver.currentJwt().getSubject()).isEqualTo("mo-100");
    }

    private static Jwt jwt(String userId, String title) {
        return Jwt.withTokenValue("token-value")
                .header("alg", "none")
                .subject(userId)
                .claim("preferred_username", userId)
                .claim("title", title)
                .claim("roles", List.of("ADMIN"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }
}
