package com.observabilitymesh.harness.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.observabilitymesh.harness.config.HarnessProperties;
import com.observabilitymesh.harness.model.SessionCredentials;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InstructionServiceClientTest {

    private MockWebServer server;
    private InstructionServiceClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        HarnessProperties properties = new HarnessProperties(
                server.url("/").toString(),
                "/api/v1",
                "http://localhost:9093",
                "/api/v1",
                "classpath:users.yaml",
                "Password1!",
                "ssi.local",
                "admin-001",
                "security_events",
                "instruction_service",
                "payment_service",
                false);
        client = new InstructionServiceClient(RestClient.builder(), properties, new ObjectMapper());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void createInstructionSendsBearerTokenAndPayload() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(201)
                .setBody("{\"instruction_id\":\"I-1\"}")
                .addHeader("Content-Type", "application/json"));

        ServiceResponse response = client.createInstruction(
                new SessionCredentials("sess-1", "token-1"),
                Map.of("owning_lob", "FICC"));

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.json().path("instruction_id").asText()).isEqualTo("I-1");

        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/api/v1/instructions");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer token-1");
        assertThat(request.getHeader("X-Session-Id")).isEqualTo("sess-1");
    }

    @Test
    void listInstructionsAppendsStatusQuery() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("[]")
                .addHeader("Content-Type", "application/json"));

        client.listInstructions(new SessionCredentials("s", "t"), "DRAFT", 100);

        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/api/v1/instructions?limit=100&status=DRAFT");
    }

    @Test
    void listInstructionsOmitsBlankStatus() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("[]"));

        client.listInstructions(new SessionCredentials("s", "t"), null, 10);

        assertThat(server.takeRequest().getPath()).isEqualTo("/api/v1/instructions?limit=10");
    }

    @Test
    void rejectInstructionPostsReason() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));

        client.rejectInstruction(new SessionCredentials("s", "t"), "I-1", "nope");

        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/api/v1/instructions/I-1/reject");
        assertThat(request.getBody().readUtf8()).contains("nope");
    }

    @Test
    void lifecycleEndpointsUseExpectedPaths() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":\"SUBMITTED\"}"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":\"APPROVED\"}"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":\"SUSPENDED\"}"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":\"REACTIVATED\"}"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("[]"));

        SessionCredentials session = new SessionCredentials("s", "t");
        assertThat(client.submitInstruction(session, "I-1").isSuccess()).isTrue();
        assertThat(client.approveInstruction(session, "I-1").isSuccess()).isTrue();
        assertThat(client.suspendInstruction(session, "I-1").isSuccess()).isTrue();
        assertThat(client.reactivateInstruction(session, "I-1").isSuccess()).isTrue();
        assertThat(client.listVersions(session, "I-1").isSuccess()).isTrue();

        assertThat(server.getRequestCount()).isEqualTo(5);
    }

    @Test
    void getInstructionReturnsPayload() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"instruction_id\":\"I-9\"}")
                .addHeader("Content-Type", "application/json"));

        ServiceResponse response = client.getInstruction(new SessionCredentials("s", "t"), "I-9");

        assertThat(response.json().path("instruction_id").asText()).isEqualTo("I-9");
    }

    @Test
    void parseJsonHandlesBlankAndInvalidBodies() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(""));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("not-json"));

        assertThat(client.getInstruction(new SessionCredentials("s", "t"), "I-1").json()).isNull();
        assertThat(client.getInstruction(new SessionCredentials("s", "t"), "I-2").json()).isNull();
    }
}
