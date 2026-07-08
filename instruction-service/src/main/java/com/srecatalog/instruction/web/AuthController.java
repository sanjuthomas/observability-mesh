package com.srecatalog.instruction.web;

import com.srecatalog.auth.KeycloakLoginClient;
import com.srecatalog.instruction.web.dto.LoginRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AuthController {

    private final KeycloakLoginClient loginClient;

    public AuthController(KeycloakLoginClient loginClient) {
        this.loginClient = loginClient;
    }

    @PostMapping("/api/auth/login")
    public Map<String, String> login(@Valid @RequestBody LoginRequest request) {
        KeycloakLoginClient.LoginResponse response = loginClient.login(request.userId(), request.password());
        return Map.of(
                "user_id", response.userId(),
                "session_token", response.sessionToken(),
                "session_id", response.sessionId() == null ? "" : response.sessionId());
    }
}
