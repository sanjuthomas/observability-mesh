package com.observabilitymesh.authz.opa;

import com.observabilitymesh.common.model.PolicyDecision;
import com.observabilitymesh.common.model.Subject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpaClientTest {

    private MockRestServiceServer server;
    private OpaClient opaClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        opaClient = new OpaClient(builder, "http://localhost");
    }

    @AfterEach
    void tearDown() {
        server.verify();
    }

    @Test
    void evaluateInstructionAllowPath() {
        expectJson(dataUrl("instruction/lifecycle/allow"), "{\"result\":true}");
        expectJson(dataUrl("instruction/lifecycle/allow_basis"), "{\"result\":[\"ROLE_MATCH\"]}");

        PolicyDecision decision = opaClient.evaluateInstruction(
                "CREATE",
                subject(),
                Map.of("status", "DRAFT"),
                Map.of("owning_lob", "FICC"));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.allowBasis()).containsExactly("ROLE_MATCH");
    }

    @Test
    void evaluateInstructionDenyPath() {
        expectJson(dataUrl("instruction/lifecycle/allow"), "{\"result\":false}");
        expectJson(dataUrl("instruction/lifecycle/violations"), "{\"result\":{\"SEGREGATION_OF_DUTIES\":true}}");
        expectJson(dataUrl("instruction/lifecycle/is_alert"), "{\"result\":true}");

        PolicyDecision decision = opaClient.evaluateInstruction(
                "APPROVE", subject(), Map.of("status", "SUBMITTED"), Map.of("owning_lob", "FICC"));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.violations()).containsExactly("SEGREGATION_OF_DUTIES");
        assertThat(decision.isAlert()).isTrue();
    }

    @Test
    void policyHealthReportsDegradedWhenPoliciesMissing() {
        expectJson("http://localhost/v1/policies", "{\"result\":[]}");

        Map<String, Object> health = opaClient.policyHealth(11);
        assertThat(health.get("ok")).isEqualTo(false);
    }

    @Test
    void policyHealthReportsUpWhenSmokePasses() {
        StringBuilder policies = new StringBuilder("{\"result\":[");
        for (int i = 0; i < 11; i++) {
            if (i > 0) {
                policies.append(',');
            }
            policies.append("{\"id\":\"policy-").append(i).append("\"}");
        }
        policies.append("]}");
        expectJson("http://localhost/v1/policies", policies.toString());
        expectJson(dataUrl("instruction/lifecycle/allow"), "{\"result\":true}");

        Map<String, Object> health = opaClient.policyHealth(11);
        assertThat(health.get("ok")).isEqualTo(true);
        assertThat(health.get("policy_count")).isEqualTo(11);
    }

    @Test
    void canApproveInstructionReturnsBasis() {
        expectJson(dataUrl("instruction/lifecycle/allow"), "{\"result\":true}");
        expectJson(dataUrl("instruction/lifecycle/allow_basis"), "{\"result\":[\"TITLE_MATCH\"]}");

        OpaClient.ApprovalResult result = opaClient.canApproveInstruction(
                subject(), Map.of("status", "SUBMITTED"), Map.of("owning_lob", "FICC"));

        assertThat(result.allowed()).isTrue();
        assertThat(result.allowBasis()).containsExactly("TITLE_MATCH");
    }

    @Test
    void evaluatePaymentAllowPathIncludesInstructionContext() {
        expectJson(dataUrl("payment/lifecycle/allow"), "{\"result\":true}");
        expectJson(dataUrl("payment/lifecycle/allow_basis"), "{\"result\":[\"LOB_MATCH\"]}");

        PolicyDecision decision = opaClient.evaluatePayment(
                "APPROVE",
                subject(),
                Map.of("status", "SUBMITTED"),
                "2027-01-01",
                "APPROVED");

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.allowBasis()).containsExactly("LOB_MATCH");
    }

    @Test
    void listPolicyIdsReturnsEmptyWhenOpaReturnsNull() {
        expectJson("http://localhost/v1/policies", "{}");
        assertThat(opaClient.listPolicyIds()).isEmpty();
    }

    @Test
    void policyHealthReportsFailureWhenSmokeDenied() {
        StringBuilder policies = new StringBuilder("{\"result\":[");
        for (int i = 0; i < 11; i++) {
            if (i > 0) {
                policies.append(',');
            }
            policies.append("{\"id\":\"policy-").append(i).append("\"}");
        }
        policies.append("]}");
        expectJson("http://localhost/v1/policies", policies.toString());
        expectJson(dataUrl("instruction/lifecycle/allow"), "{\"result\":false}");

        Map<String, Object> health = opaClient.policyHealth(11);
        assertThat(health.get("ok")).isEqualTo(false);
        assertThat(health.get("detail")).isEqualTo("instruction CREATE smoke evaluation denied");
    }

    @Test
    void canApprovePaymentReturnsDeniedWithoutBasis() {
        expectJson(dataUrl("payment/lifecycle/allow"), "{\"result\":false}");

        OpaClient.ApprovalResult result = opaClient.canApprovePayment(subject(), Map.of("status", "SUBMITTED"));
        assertThat(result.allowed()).isFalse();
        assertThat(result.allowBasis()).isEmpty();
    }

    @Test
    void evaluatePaymentDenyPathCollectsViolations() {
        expectJson(dataUrl("payment/lifecycle/allow"), "{\"result\":false}");
        expectJson(dataUrl("payment/lifecycle/violations"), "{\"result\":{\"AMOUNT_LIMIT\":true}}");
        expectJson(dataUrl("payment/lifecycle/is_alert"), "{\"result\":false}");

        PolicyDecision decision = opaClient.evaluatePayment(
                "APPROVE", subject(), Map.of("status", "SUBMITTED"), null, null);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.violations()).containsExactly("AMOUNT_LIMIT");
        assertThat(decision.isAlert()).isFalse();
    }

    @Test
    void policyHealthReportsExceptionDetail() {
        server.expect(requestTo("http://localhost/v1/policies"))
                .andRespond(withSuccess("not-json", MediaType.TEXT_PLAIN));

        Map<String, Object> health = opaClient.policyHealth(11);
        assertThat(health.get("ok")).isEqualTo(false);
        assertThat(health.get("policy_count")).isEqualTo(0);
    }

    private static Subject subject() {
        return new Subject(
                "mo-100", "Sarah", "Chen", "Analyst", null,
                List.of("INSTRUCTION_CREATOR"), List.of("MIDDLE_OFFICE"), null, List.of(), null, List.of());
    }

    private void expectJson(String path, String body) {
        server.expect(requestTo(path))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private static String dataUrl(String packagePath) {
        return "http://localhost/v1/data/" + packagePath.replace("/", "%2F");
    }
}
