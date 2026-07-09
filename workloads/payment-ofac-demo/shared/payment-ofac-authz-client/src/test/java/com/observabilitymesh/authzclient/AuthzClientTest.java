package com.observabilitymesh.authzclient;

import com.observabilitymesh.common.model.PolicyDecision;
import com.observabilitymesh.common.model.Subject;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthzClientTest {

    private MockWebServer server;
    private AuthzClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new AuthzClient(RestClient.builder(), server.url("/").toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void evaluateInstructionUsesOnBehalfOfHeaders() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"allowed":true,"allowBasis":["role gate"],"violations":[],"isAlert":false}
                        """));

        Subject subject = new Subject("mo-100", "Sarah", "Chen", "Analyst", null,
                List.of("INSTRUCTION_CREATOR"), List.of(), null, List.of(), null, List.of());
        PolicyDecision decision = client.evaluateInstruction(
                "CREATE",
                Map.of("instruction_id", "inst-1"),
                Map.of(),
                "svc-token",
                "svc-session",
                "user-token",
                "user-session",
                subject);

        assertThat(decision.allowed()).isTrue();
        RecordedRequest request = server.takeRequest();
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer svc-token");
        assertThat(request.getHeader("X-On-Behalf-Of")).isEqualTo("user-token");
    }

    @Test
    void eligiblePaymentApproversMapsResponse() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"paymentId":"pay-1","instructionId":"inst-1","paymentStatus":"SUBMITTED",
                        "amount":1000.0,"currency":"USD","owningLob":"FICC","instructionStatus":"APPROVED",
                        "evaluatedAt":"2026-07-07T00:00:00Z","eligible":[],"prospectiveEligible":[],
                        "candidatesEvaluated":3,"approvalBlockedReason":null}
                        """));

        Map<String, Object> result = client.eligiblePaymentApprovers(
                Map.of("payment_id", "pay-1"),
                "APPROVED",
                "2026-07-08",
                "svc-token",
                "svc-session");

        assertThat(result).containsEntry("payment_id", "pay-1");
        assertThat(result).containsEntry("candidates_evaluated", 3);
    }

    @Test
    void evaluateInstructionWithoutOboIncludesSubjectPayload() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"allowed":false,"allowBasis":[],"violations":["denied"],"isAlert":true}
                        """));

        Subject subject = new Subject("mo-100", "Sarah", "Chen", "Analyst", null,
                List.of("INSTRUCTION_CREATOR"), List.of(), null, List.of(), null, List.of());
        PolicyDecision decision = client.evaluateInstruction(
                "CREATE",
                Map.of("instruction_id", "inst-1"),
                Map.of("account_id", "acct-1"),
                "Bearer svc-token",
                "svc-session",
                null,
                null,
                subject);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.isAlert()).isTrue();
        RecordedRequest request = server.takeRequest();
        assertThat(request.getBody().readUtf8()).contains("subject");
    }

    @Test
    void evaluatePaymentUsesOnBehalfOfHeaders() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"allowed":true,"allowBasis":["ok"],"violations":[],"isAlert":false}
                        """));

        Subject subject = new Subject("pay-101", "Alex", "Nguyen", "Analyst", "FICC",
                List.of("PAYMENT_CREATOR"), List.of(), null, List.of(), null, List.of());
        PolicyDecision decision = client.evaluatePayment(
                "CREATE",
                Map.of("payment_id", "pay-1"),
                "2026-07-08",
                "APPROVED",
                "svc-token",
                "svc-session",
                "user-token",
                "user-session",
                subject);

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void eligibleInstructionApproversMapsResponse() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"instructionId":"inst-1","instructionStatus":"SUBMITTED","instructionType":"DOMESTIC",
                        "owningLob":"FICC","createdByUserId":"mo-100","createdByTitle":"Analyst",
                        "evaluatedAt":"2026-07-07T00:00:00Z","eligible":[],"prospectiveEligible":[],
                        "candidatesEvaluated":2,"approvalBlockedReason":null}
                        """));

        Map<String, Object> result = client.eligibleInstructionApprovers(
                Map.of("instruction_id", "inst-1"),
                "svc-token",
                "svc-session");

        assertThat(result).containsEntry("instruction_id", "inst-1");
        assertThat(result).containsEntry("candidates_evaluated", 2);
    }

    @Test
    void eligibleInstructionApproversFailsOnEmptyResponse() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("null"));

        assertThatThrownBy(() -> client.eligibleInstructionApprovers(
                Map.of("instruction_id", "inst-1"),
                "svc-token",
                "svc-session"))
                .isInstanceOf(AuthzClientException.class);
    }

    @Test
    void evaluateInstructionFailsOnEmptyResponse() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("null"));

        Subject subject = new Subject("mo-100", "Sarah", "Chen", "Analyst", null,
                List.of("INSTRUCTION_CREATOR"), List.of(), null, List.of(), null, List.of());

        assertThatThrownBy(() -> client.evaluateInstruction(
                "CREATE",
                Map.of("instruction_id", "inst-1"),
                Map.of(),
                "svc-token",
                null,
                null,
                null,
                subject))
                .isInstanceOf(AuthzClientException.class);
    }

    @Test
    void evaluateInstructionWithOboSessionHeader() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"allowed":true,"allowBasis":[],"violations":[],"isAlert":false}
                        """));

        Subject subject = new Subject("mo-100", "Sarah", "Chen", "Analyst", null,
                List.of("INSTRUCTION_CREATOR"), List.of(), null, List.of(), null, List.of());
        client.evaluateInstruction(
                "APPROVE",
                Map.of("instruction_id", "inst-1"),
                Map.of(),
                "svc-token",
                "svc-session",
                "user-token",
                "user-session",
                subject);

        RecordedRequest request = server.takeRequest();
        assertThat(request.getHeader("X-On-Behalf-Of-Session-Id")).isEqualTo("user-session");
    }

    @Test
    void stripsTrailingSlashFromBaseUrl() {
        AuthzClient trailingSlashClient = new AuthzClient(RestClient.builder(), server.url("/").toString() + "/");
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"allowed":true,"allowBasis":[],"violations":[],"isAlert":false}
                        """));

        Subject subject = new Subject("mo-100", "Sarah", "Chen", "Analyst", null,
                List.of("INSTRUCTION_CREATOR"), List.of(), null, List.of(), null, List.of());
        trailingSlashClient.evaluateInstruction(
                "CREATE",
                Map.of("instruction_id", "inst-1"),
                Map.of(),
                null,
                null,
                null,
                null,
                subject);

        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void serviceHeadersAcceptBearerPrefixedToken() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"allowed":true,"allowBasis":[],"violations":[],"isAlert":false}
                        """));

        Subject subject = new Subject("mo-100", "Sarah", "Chen", "Analyst", null,
                List.of("INSTRUCTION_CREATOR"), List.of(), null, List.of(), null, List.of());
        client.evaluateInstruction(
                "CREATE",
                Map.of("instruction_id", "inst-1"),
                Map.of(),
                "Bearer svc-token",
                "svc-session",
                null,
                null,
                subject);

        RecordedRequest request = server.takeRequest();
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer svc-token");
        assertThat(request.getHeader("X-Session-Id")).isEqualTo("svc-session");
    }

    @Test
    void evaluatePaymentWithoutOboIncludesSubjectPayload() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"allowed":true,"allowBasis":[],"violations":[],"isAlert":false}
                        """));

        Subject subject = new Subject("pay-101", "Alex", "Nguyen", "Analyst", "FICC",
                List.of("PAYMENT_CREATOR"), List.of(), null, List.of(), null, List.of());
        client.evaluatePayment(
                "CREATE",
                Map.of("payment_id", "pay-1"),
                "2026-07-08",
                "APPROVED",
                "svc-token",
                null,
                null,
                null,
                subject);

        RecordedRequest request = server.takeRequest();
        assertThat(request.getBody().readUtf8()).contains("subject");
    }

    @Test
    void evaluatePaymentWithOboOmitsBlankUserSessionHeader() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"allowed":true,"allowBasis":[],"violations":[],"isAlert":false}
                        """));

        Subject subject = new Subject("pay-101", "Alex", "Nguyen", "Analyst", "FICC",
                List.of("PAYMENT_CREATOR"), List.of(), null, List.of(), null, List.of());
        client.evaluatePayment(
                "APPROVE",
                Map.of("payment_id", "pay-1"),
                "2026-07-08",
                "APPROVED",
                "svc-token",
                "svc-session",
                "user-token",
                " ",
                subject);

        RecordedRequest request = server.takeRequest();
        assertThat(request.getHeader("X-On-Behalf-Of-Session-Id")).isNull();
    }

    @Test
    void eligiblePaymentApproversFailsOnEmptyResponse() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("null"));

        assertThatThrownBy(() -> client.eligiblePaymentApprovers(
                Map.of("payment_id", "pay-1"),
                "APPROVED",
                "2026-07-08",
                "svc-token",
                "svc-session"))
                .isInstanceOf(AuthzClientException.class);
    }
}
