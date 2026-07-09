package com.observabilitymesh.instruction.web;

import com.observabilitymesh.auth.KeycloakLoginClient;
import com.observabilitymesh.instruction.web.dto.LoginRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock KeycloakLoginClient loginClient;

    @Test
    void loginReturnsSessionFields() {
        when(loginClient.login("admin-001", "secret"))
                .thenReturn(new KeycloakLoginClient.LoginResponse("admin-001", "token", "sess-1"));
        AuthController controller = new AuthController(loginClient);

        var response = controller.login(new LoginRequest("admin-001", "secret"));
        assertThat(response.get("session_token")).isEqualTo("token");
        assertThat(response.get("session_id")).isEqualTo("sess-1");
    }

    @Test
    void loginMapsNullSessionIdToEmptyString() {
        when(loginClient.login("admin-001", "secret"))
                .thenReturn(new KeycloakLoginClient.LoginResponse("admin-001", "token", null));
        AuthController controller = new AuthController(loginClient);

        var response = controller.login(new LoginRequest("admin-001", "secret"));
        assertThat(response.get("session_id")).isEqualTo("");
    }
}
