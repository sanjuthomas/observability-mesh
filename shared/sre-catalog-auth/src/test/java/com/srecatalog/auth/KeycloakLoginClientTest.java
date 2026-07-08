package com.srecatalog.auth;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeycloakLoginClientTest {

    private MockWebServer server;
    private KeycloakLoginClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new KeycloakLoginClient(
                RestClient.builder(),
                server.url("/token").toString(),
                "sre-catalog-ui",
                "");
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void loginReturnsAccessToken() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"access_token":"token-abc","session_state":"sess-1"}
                        """));

        KeycloakLoginClient.LoginResponse response = client.login("mo-100", "Password1!");

        assertThat(response.userId()).isEqualTo("mo-100");
        assertThat(response.sessionToken()).isEqualTo("token-abc");
        assertThat(response.sessionId()).isEqualTo("sess-1");
    }

    @Test
    void loginWithoutClientSecret() {
        KeycloakLoginClient publicClient = new KeycloakLoginClient(
                RestClient.builder(),
                server.url("/token").toString(),
                "public-client",
                null);
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"access_token":"token-abc","session_state":"sess-1"}
                        """));

        KeycloakLoginClient.LoginResponse response = publicClient.login("mo-100", "Password1!");

        assertThat(response.sessionToken()).isEqualTo("token-abc");
    }

    @Test
    void loginFailsWhenTokenMissing() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{}"));

        assertThatThrownBy(() -> client.login("mo-100", "bad"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Keycloak login failed");
    }

    @Test
    void loginAllowsNullSessionState() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"access_token":"token-abc"}
                        """));

        KeycloakLoginClient.LoginResponse response = client.login("mo-100", "Password1!");

        assertThat(response.sessionId()).isNull();
    }
}
