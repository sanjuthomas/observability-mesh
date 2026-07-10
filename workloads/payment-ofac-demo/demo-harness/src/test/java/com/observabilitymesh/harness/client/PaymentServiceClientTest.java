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

import static org.assertj.core.api.Assertions.assertThat;

class PaymentServiceClientTest {

    private MockWebServer server;
    private PaymentServiceClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        HarnessProperties properties = new HarnessProperties(
                "http://localhost:9000",
                "/api/v1",
                server.url("/").toString(),
                "/api/v1",
                "classpath:users.yaml",
                "Password1!",
                "ssi.local",
                "admin-001",
                "security_events",
                "instruction_service",
                "payment_service",
                false);
        client = new PaymentServiceClient(RestClient.builder(), properties, new ObjectMapper());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void createPaymentPostsInstructionAmountAndValueDate() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(201)
                .setBody("{\"payment_id\":\"P-1\"}")
                .addHeader("Content-Type", "application/json"));

        ServiceResponse response = client.createPayment(
                new SessionCredentials("sess", "token"), "I-1", 1_000_000.0, "2026-07-08");

        assertThat(response.statusCode()).isEqualTo(201);
        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/api/v1/payments");
        assertThat(request.getBody().readUtf8()).contains("I-1");
    }

    @Test
    void paymentLifecycleEndpointsUseExpectedPaths() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":\"SUBMITTED\"}"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":\"APPROVED\"}"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":\"REJECTED\"}"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"version_number\":2,\"amount\":2000000}"));

        SessionCredentials session = new SessionCredentials("s", "t");
        assertThat(client.submitPayment(session, "P-1").isSuccess()).isTrue();
        assertThat(client.approvePayment(session, "P-1").isSuccess()).isTrue();
        assertThat(client.rejectPayment(session, "P-1", "no").isSuccess()).isTrue();
        assertThat(client.updatePayment(session, "P-1", "I-1", 2_000_000.0, "2026-07-01").isSuccess()).isTrue();

        assertThat(server.getRequestCount()).isEqualTo(4);
    }

    @Test
    void listPaymentsAppendsStatusQuery() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("[]"));

        client.listPayments(new SessionCredentials("s", "t"), "SUBMITTED", 50);

        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/api/v1/payments?limit=50&status=SUBMITTED");
    }

    @Test
    void listPaymentsOmitsBlankStatus() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("[]"));

        client.listPayments(new SessionCredentials("s", "t"), " ", 25);

        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/api/v1/payments?limit=25");
    }

    @Test
    void stripsTrailingSlashFromBaseUrl() throws Exception {
        HarnessProperties properties = new HarnessProperties(
                "http://localhost:9000",
                "/api/v1",
                server.url("/").toString().replaceAll("/$", "") + "/",
                "/api/v1",
                "classpath:users.yaml",
                "Password1!",
                "ssi.local",
                "admin-001",
                "security_events",
                "instruction_service",
                "payment_service",
                false);
        client = new PaymentServiceClient(RestClient.builder(), properties, new ObjectMapper());
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));

        client.submitPayment(new SessionCredentials("s", "t"), "P-1");

        assertThat(server.takeRequest().getPath()).isEqualTo("/api/v1/payments/P-1/submit");
    }

    @Test
    void parseJsonHandlesBlankAndInvalidBodies() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(""));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("not-json"));

        assertThat(client.submitPayment(new SessionCredentials("s", "t"), "P-1").json()).isNull();
        assertThat(client.submitPayment(new SessionCredentials("s", "t"), "P-2").json()).isNull();
    }
}
