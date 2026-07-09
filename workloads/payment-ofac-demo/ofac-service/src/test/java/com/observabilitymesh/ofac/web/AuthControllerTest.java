package com.observabilitymesh.ofac.web;

import com.observabilitymesh.auth.KeycloakLoginClient;
import com.observabilitymesh.ofac.web.dto.LoginRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock KeycloakLoginClient loginClient;
    @InjectMocks AuthController authController;

    @Test
    void loginReturnsSessionFields() {
        when(loginClient.login("user-001", "secret"))
                .thenReturn(new KeycloakLoginClient.LoginResponse("user-001", "token-1", "sess-1"));
        Map<String, String> response = authController.login(new LoginRequest("user-001", "secret"));
        assertThat(response.get("user_id")).isEqualTo("user-001");
        assertThat(response.get("session_token")).isEqualTo("token-1");
        assertThat(response.get("session_id")).isEqualTo("sess-1");
    }
}
