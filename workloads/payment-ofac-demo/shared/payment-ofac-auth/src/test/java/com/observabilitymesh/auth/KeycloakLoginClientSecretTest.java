package com.observabilitymesh.auth;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakLoginClientSecretTest {

    private MockWebServer server;
    private KeycloakLoginClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new KeycloakLoginClient(
                RestClient.builder(),
                server.url("/token").toString(),
                "confidential-client",
                "secret-value");
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void loginIncludesClientSecret() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"access_token":"token-abc","session_state":"sess-1"}
                        """));

        KeycloakLoginClient.LoginResponse response = client.login("svc-instruction", "Password1!");

        assertThat(response.sessionToken()).isEqualTo("token-abc");
        assertThat(server.takeRequest().getBody().readUtf8()).contains("client_secret=secret-value");
    }
}
