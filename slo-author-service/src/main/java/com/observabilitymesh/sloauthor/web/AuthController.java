package com.observabilitymesh.sloauthor.web;

import com.observabilitymesh.auth.KeycloakLoginClient;
import com.observabilitymesh.sloauthor.web.dto.LoginRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final KeycloakLoginClient loginClient;

    public AuthController(KeycloakLoginClient loginClient) {
        this.loginClient = loginClient;
    }

    @PostMapping("/login")
    public Map<String, String> login(@Valid @RequestBody LoginRequest request) {
        KeycloakLoginClient.LoginResponse response = loginClient.login(request.userId(), request.password());
        return Map.of(
            "user_id", response.userId(),
            "session_token", response.sessionToken(),
            "session_id", response.sessionId() == null ? "" : response.sessionId());
    }

    @GetMapping("/me")
    public Map<String, String> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
            || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return Map.of("username", authentication.getName());
    }
}
