package com.srecatalog.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.srecatalog.common.model.Subject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class KeycloakLoginClient {

    private final RestClient restClient;
    private final String tokenUrl;
    private final String clientId;
    private final String clientSecret;

    public KeycloakLoginClient(
            RestClient.Builder builder,
            @Value("${sre-catalog.auth.keycloak-token-url}") String tokenUrl,
            @Value("${sre-catalog.auth.keycloak-client-id:sre-catalog-ui}") String clientId,
            @Value("${sre-catalog.auth.keycloak-client-secret:}") String clientSecret) {
        this.restClient = builder.build();
        this.tokenUrl = tokenUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public LoginResponse login(String username, String password) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", clientId);
        if (clientSecret != null && !clientSecret.isBlank()) {
            form.add("client_secret", clientSecret);
        }
        form.add("username", username);
        form.add("password", password);

        TokenResponse token = restClient.post()
                .uri(tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);

        if (token == null || token.accessToken() == null) {
            throw new IllegalArgumentException("Keycloak login failed");
        }
        return new LoginResponse(username, token.accessToken(), token.sessionState());
    }

    public record LoginResponse(
            @JsonProperty("user_id") String userId,
            @JsonProperty("session_token") String sessionToken,
            @JsonProperty("session_id") String sessionId
    ) {
    }

    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("session_state") String sessionState
    ) {
    }
}
