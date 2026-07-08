package com.srecatalog.sequenceclient;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SequenceClientTest {

    private MockWebServer server;
    private SequenceClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new SequenceClient(RestClient.builder(), server.url("/").toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void nextInstructionIdReturnsSequenceId() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"sequenceId\":\"SSI-I-20260707-FICC-000001\"}"));

        assertThat(client.nextInstructionId("2026-07-07", "FICC"))
                .isEqualTo("SSI-I-20260707-FICC-000001");
    }

    @Test
    void nextSecurityEventIdReturnsSequenceId() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"sequenceId\":\"SE-000001\"}"));

        assertThat(client.nextSecurityEventId("inst-1")).isEqualTo("SE-000001");
    }

    @Test
    void nextPaymentIdReturnsSequenceId() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"sequenceId\":\"SSI-P-20260707-FICC-000001\"}"));

        assertThat(client.nextPaymentId("2026-07-07", "FICC"))
                .isEqualTo("SSI-P-20260707-FICC-000001");
    }

    @Test
    void stripsTrailingSlashFromBaseUrl() {
        SequenceClient trailingSlashClient = new SequenceClient(
                RestClient.builder(), server.url("/").toString() + "/");
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"sequenceId\":\"SSI-I-20260707-FICC-000001\"}"));

        assertThat(trailingSlashClient.nextInstructionId("2026-07-07", "FICC"))
                .isEqualTo("SSI-I-20260707-FICC-000001");
    }

    @Test
    void nextPaymentIdFailsOnEmptyResponse() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("null"));

        assertThatThrownBy(() -> client.nextPaymentId("2026-07-07", "FICC"))
                .isInstanceOf(SequenceClientException.class);
    }

    @Test
    void nextSecurityEventIdFailsOnEmptyResponse() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("null"));

        assertThatThrownBy(() -> client.nextSecurityEventId("inst-1"))
                .isInstanceOf(SequenceClientException.class);
    }
}
