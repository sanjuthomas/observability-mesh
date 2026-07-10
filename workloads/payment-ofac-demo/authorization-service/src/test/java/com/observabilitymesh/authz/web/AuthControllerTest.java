package com.observabilitymesh.authz.web;

import com.observabilitymesh.auth.KeycloakLoginClient;
import com.observabilitymesh.authz.web.dto.LoginRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock KeycloakLoginClient loginClient;
    @InjectMocks AuthController controller;

    @Test
    void loginReturnsSessionFields() {
        when(loginClient.login("admin-001", "Password1!"))
                .thenReturn(new KeycloakLoginClient.LoginResponse("admin-001", "token-1", "sess-1"));

        var response = controller.login(new LoginRequest("admin-001", "Password1!"));

        assertThat(response).containsEntry("user_id", "admin-001");
        assertThat(response).containsEntry("session_token", "token-1");
        assertThat(response).containsEntry("session_id", "sess-1");
    }

    @Test
    void loginMapsNullSessionIdToEmptyString() {
        when(loginClient.login("admin-001", "Password1!"))
                .thenReturn(new KeycloakLoginClient.LoginResponse("admin-001", "token-1", null));

        var response = controller.login(new LoginRequest("admin-001", "Password1!"));

        assertThat(response.get("session_id")).isEqualTo("");
    }
}
