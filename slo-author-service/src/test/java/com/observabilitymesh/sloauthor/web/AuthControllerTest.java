package com.observabilitymesh.sloauthor.web;

import com.observabilitymesh.auth.KeycloakLoginClient;
import com.observabilitymesh.sloauthor.web.dto.LoginRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private KeycloakLoginClient loginClient;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void loginReturnsSessionFields() {
        when(loginClient.login("alice", "secret"))
            .thenReturn(new KeycloakLoginClient.LoginResponse("alice", "token", "sess-1"));
        AuthController controller = new AuthController(loginClient);

        var response = controller.login(new LoginRequest("alice", "secret"));

        assertThat(response.get("user_id")).isEqualTo("alice");
        assertThat(response.get("session_token")).isEqualTo("token");
        assertThat(response.get("session_id")).isEqualTo("sess-1");
    }

    @Test
    void loginMapsNullSessionIdToEmptyString() {
        when(loginClient.login("alice", "secret"))
            .thenReturn(new KeycloakLoginClient.LoginResponse("alice", "token", null));
        AuthController controller = new AuthController(loginClient);

        var response = controller.login(new LoginRequest("alice", "secret"));

        assertThat(response.get("session_id")).isEqualTo("");
    }

    @Test
    void currentUserReturnsUsernameFromJwt() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("alice")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthenticationToken(jwt, java.util.List.of()));
        AuthController controller = new AuthController(loginClient);

        assertThat(controller.currentUser().get("username")).isEqualTo("alice");
    }

    @Test
    void currentUserRejectsMissingAuthentication() {
        SecurityContextHolder.clearContext();
        AuthController controller = new AuthController(loginClient);

        assertThatThrownBy(controller::currentUser)
            .isInstanceOf(ResponseStatusException.class);
    }
}
