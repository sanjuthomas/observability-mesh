package com.srecatalog.harness.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.srecatalog.auth.KeycloakLoginClient;
import com.srecatalog.harness.HarnessTestFixtures;
import com.srecatalog.harness.client.InstructionServiceClient;
import com.srecatalog.harness.client.PaymentServiceClient;
import com.srecatalog.harness.client.ServiceResponse;
import com.srecatalog.harness.config.HarnessProperties;
import com.srecatalog.harness.model.SessionCredentials;
import com.srecatalog.harness.seed.SeedFile;
import com.srecatalog.harness.seed.SeedFileLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HarnessActionsTest {

    @Mock SeedFileLoader seedFileLoader;
    @Mock InstructionServiceClient instructionClient;
    @Mock PaymentServiceClient paymentClient;
    @Mock SecurityEventCounter securityEventCounter;
    @Mock KeycloakLoginClient loginClient;

    private HarnessActions actions;

    @BeforeEach
    void setUp() {
        HarnessProperties properties = new HarnessProperties(
                "http://localhost:9000",
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
        HarnessHelpers helpers = new HarnessHelpers(properties, loginClient, instructionClient, paymentClient);
        actions = new HarnessActions(
                properties, seedFileLoader, helpers, instructionClient, paymentClient, securityEventCounter);
        when(seedFileLoader.load()).thenReturn(HarnessTestFixtures.SAMPLE_SEED);
        when(loginClient.login(anyString(), anyString()))
                .thenReturn(new KeycloakLoginClient.LoginResponse("user", "token", "sess"));
    }

    @Test
    void createInstructionsRecordsSuccess() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var json = mapper.readTree("{\"instruction_id\":\"I-1\"}");
        when(instructionClient.createInstruction(any(), any(Map.class)))
                .thenReturn(new ServiceResponse(201, json.toString(), json));

        var result = actions.createInstructions(1, new SessionCredentials("s", "t"));

        assertThat(result.ok()).isTrue();
        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.logs()).anyMatch(line -> line.contains("I-1"));
    }

    @Test
    void createPaymentsFailsWithoutApprovedInstructions() {
        when(instructionClient.listInstructions(any(), eq("APPROVED"), anyInt()))
                .thenReturn(new ServiceResponse(200, "[]", new ObjectMapper().createArrayNode()));

        var result = actions.createPayments(1, new SessionCredentials("s", "t"));

        assertThat(result.ok()).isFalse();
        assertThat(result.logs()).anyMatch(line -> line.contains("No APPROVED instructions"));
    }

    @Test
    void submitInstructionsHandlesEmptyDrafts() {
        when(instructionClient.listInstructions(any(), anyString(), anyInt()))
                .thenReturn(new ServiceResponse(200, "[]", new ObjectMapper().createArrayNode()));

        var result = actions.submitInstructions(3, new SessionCredentials("s", "t"));

        assertThat(result.ok()).isTrue();
        assertThat(result.succeeded()).isZero();
        assertThat(result.logs()).anyMatch(line -> line.contains("No DRAFT instructions"));
    }

    @Test
    void updatePaymentsUsesOverrideAmount() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var listJson = mapper.readTree("""
                [{"payment_id":"P-1","instruction_id":"I-1","value_date":"2026-07-01","amount":1000000,
                  "created_by":{"user_id":"pay-101"}}]
                """);
        var updateJson = mapper.readTree("{\"version_number\":2,\"amount\":2000000}");
        when(paymentClient.listPayments(any(), anyString(), anyInt()))
                .thenReturn(new ServiceResponse(200, listJson.toString(), listJson));
        when(paymentClient.updatePayment(any(), anyString(), anyString(), anyDouble(), anyString()))
                .thenReturn(new ServiceResponse(200, updateJson.toString(), updateJson));

        var result = actions.updatePayments(1, new SessionCredentials("s", "t"), 2_000_000.0);

        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.ok()).isTrue();
    }

    @Test
    void submitInstructionsSubmitsDrafts() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var drafts = mapper.readTree("[{\"instruction_id\":\"I-1\",\"owning_lob\":\"FICC\",\"status\":\"DRAFT\"}]");
        when(instructionClient.listInstructions(any(), eq("DRAFT"), anyInt()))
                .thenReturn(new ServiceResponse(200, drafts.toString(), drafts));
        when(instructionClient.submitInstruction(any(), eq("I-1")))
                .thenReturn(new ServiceResponse(200, "{}", mapper.createObjectNode()));

        var result = actions.submitInstructions(1, new SessionCredentials("s", "t"));

        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.ok()).isTrue();
    }

    @Test
    void approveInstructionsSubmitsDraftThenApproves() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var drafts = mapper.readTree("""
                [{"instruction_id":"I-1","owning_lob":"FICC","status":"DRAFT",
                  "created_by":{"user_id":"mo-100","title":"Analyst","supervisor_id":"mo-050"}}]
                """);
        when(instructionClient.listInstructions(any(), eq("DRAFT"), anyInt()))
                .thenReturn(new ServiceResponse(200, drafts.toString(), drafts));
        when(instructionClient.listInstructions(any(), eq("SUBMITTED"), anyInt()))
                .thenReturn(new ServiceResponse(200, "[]", mapper.createArrayNode()));
        when(instructionClient.submitInstruction(any(), eq("I-1")))
                .thenReturn(new ServiceResponse(200, "{}", mapper.createObjectNode()));
        when(instructionClient.approveInstruction(any(), eq("I-1")))
                .thenReturn(new ServiceResponse(200, "{\"status\":\"APPROVED\"}", mapper.readTree("{\"status\":\"APPROVED\"}")));

        var result = actions.approveInstructions(1, new SessionCredentials("s", "t"));

        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.ok()).isTrue();
    }

    @Test
    void rejectInstructionsSkipsWhenNoApprover() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var submitted = mapper.readTree("""
                [{"instruction_id":"I-9","owning_lob":"UNKNOWN","status":"SUBMITTED",
                  "created_by":{"user_id":"mo-100","title":"Analyst"}}]
                """);
        when(instructionClient.listInstructions(any(), eq("SUBMITTED"), anyInt()))
                .thenReturn(new ServiceResponse(200, submitted.toString(), submitted));

        var result = actions.rejectInstructions(1, new SessionCredentials("s", "t"));

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
    }

    @Test
    void suspendAndReactivateInstructions() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var approved = mapper.readTree("[{\"instruction_id\":\"I-1\",\"owning_lob\":\"FICC\",\"status\":\"APPROVED\"}]");
        var suspended = mapper.readTree("[{\"instruction_id\":\"I-1\",\"owning_lob\":\"FICC\",\"status\":\"SUSPENDED\"}]");
        when(instructionClient.listInstructions(any(), eq("APPROVED"), anyInt()))
                .thenReturn(new ServiceResponse(200, approved.toString(), approved));
        when(instructionClient.listInstructions(any(), eq("SUSPENDED"), anyInt()))
                .thenReturn(new ServiceResponse(200, suspended.toString(), suspended));
        when(instructionClient.suspendInstruction(any(), eq("I-1")))
                .thenReturn(new ServiceResponse(200, "{}", mapper.createObjectNode()));
        when(instructionClient.reactivateInstruction(any(), eq("I-1")))
                .thenReturn(new ServiceResponse(200, "{\"status\":\"REACTIVATED\"}", mapper.readTree("{\"status\":\"REACTIVATED\"}")));

        assertThat(actions.suspendInstructions(1, new SessionCredentials("s", "t")).succeeded()).isEqualTo(1);
        assertThat(actions.reactivateInstructions(1, new SessionCredentials("s", "t")).succeeded()).isEqualTo(1);
    }

    @Test
    void runPolicyScenarioTracksFailures() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var created = mapper.readTree("{\"instruction_id\":\"I-1\"}");
        when(instructionClient.createInstruction(any(), any(Map.class)))
                .thenReturn(new ServiceResponse(201, created.toString(), created));
        when(instructionClient.getInstruction(any(), eq("I-1")))
                .thenReturn(new ServiceResponse(200, created.toString(), created));
        when(instructionClient.submitInstruction(any(), eq("I-1")))
                .thenReturn(new ServiceResponse(200, "{}", mapper.createObjectNode()));
        when(instructionClient.listInstructions(any(), any(), anyInt()))
                .thenReturn(new ServiceResponse(200, "[]", mapper.createArrayNode()));
        when(instructionClient.approveInstruction(any(), eq("I-1")))
                .thenReturn(new ServiceResponse(403, "denied", mapper.readTree("{\"detail\":\"denied\"}")));
        when(instructionClient.listVersions(any(), eq("I-1")))
                .thenReturn(new ServiceResponse(403, "denied", mapper.readTree("{\"detail\":\"denied\"}")));

        var result = actions.runPolicyScenario(new SessionCredentials("s", "t"));

        assertThat(result.logs()).isNotEmpty();
    }

    @Test
    void createPaymentsUsesApprovedStandingInstruction() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var approved = mapper.readTree("""
                [{"instruction_id":"I-1","owning_lob":"FICC","instruction_type":"STANDING","status":"APPROVED"}]
                """);
        var payment = mapper.readTree("{\"payment_id\":\"P-1\"}");
        when(instructionClient.listInstructions(any(), eq("APPROVED"), anyInt()))
                .thenReturn(new ServiceResponse(200, approved.toString(), approved));
        when(paymentClient.createPayment(any(), eq("I-1"), anyDouble(), anyString()))
                .thenReturn(new ServiceResponse(201, payment.toString(), payment));

        var result = actions.createPayments(1, new SessionCredentials("s", "t"));

        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.ok()).isTrue();
    }

    @Test
    void submitApproveAndRejectPayments() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var draft = mapper.readTree("""
                [{"payment_id":"P-1","owning_lob":"FICC","status":"DRAFT","amount":1000000,
                  "created_by":{"user_id":"pay-101"}}]
                """);
        var submitted = mapper.readTree("""
                [{"payment_id":"P-1","owning_lob":"FICC","status":"SUBMITTED","amount":1000000,
                  "created_by":{"user_id":"pay-101","title":"Analyst"}}]
                """);
        when(paymentClient.listPayments(any(), eq("DRAFT"), anyInt()))
                .thenReturn(new ServiceResponse(200, draft.toString(), draft));
        when(paymentClient.listPayments(any(), eq("SUBMITTED"), anyInt()))
                .thenReturn(new ServiceResponse(200, submitted.toString(), submitted));
        when(paymentClient.submitPayment(any(), eq("P-1")))
                .thenReturn(new ServiceResponse(200, "{}", mapper.createObjectNode()));
        when(paymentClient.approvePayment(any(), eq("P-1")))
                .thenReturn(new ServiceResponse(200, "{}", mapper.createObjectNode()));
        when(paymentClient.rejectPayment(any(), eq("P-1"), anyString()))
                .thenReturn(new ServiceResponse(200, "{}", mapper.createObjectNode()));

        assertThat(actions.submitPayments(1, new SessionCredentials("s", "t")).succeeded()).isEqualTo(1);
        assertThat(actions.approvePayments(1, new SessionCredentials("s", "t")).succeeded()).isEqualTo(1);
        assertThat(actions.rejectPayments(1, new SessionCredentials("s", "t")).succeeded()).isEqualTo(1);
    }

    @Test
    void runPaymentPolicyScenarioFailsWithoutApprovedFiccInstruction() {
        when(instructionClient.listInstructions(any(), eq("APPROVED"), anyInt()))
                .thenReturn(new ServiceResponse(200, "[]", new ObjectMapper().createArrayNode()));

        var result = actions.runPaymentPolicyScenario(new SessionCredentials("s", "t"));

        assertThat(result.ok()).isFalse();
    }

    @Test
    void createInstructionsRecordsFailure() throws Exception {
        when(instructionClient.createInstruction(any(), any(Map.class)))
                .thenReturn(new ServiceResponse(403, "{\"detail\":\"denied\"}", new ObjectMapper().readTree("{\"detail\":\"denied\"}")));

        var result = actions.createInstructions(1, new SessionCredentials("s", "t"));

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.ok()).isFalse();
    }

    @Test
    void rejectInstructionsRejectsSubmitted() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var submitted = mapper.readTree("""
                [{"instruction_id":"I-1","owning_lob":"FICC","status":"SUBMITTED",
                  "created_by":{"user_id":"mo-100","title":"Analyst","supervisor_id":"mo-050"}}]
                """);
        when(instructionClient.listInstructions(any(), eq("SUBMITTED"), anyInt()))
                .thenReturn(new ServiceResponse(200, submitted.toString(), submitted));
        when(instructionClient.rejectInstruction(any(), eq("I-1"), anyString()))
                .thenReturn(new ServiceResponse(200, "{}", mapper.createObjectNode()));

        var result = actions.rejectInstructions(1, new SessionCredentials("s", "t"));

        assertThat(result.succeeded()).isEqualTo(1);
    }

    @Test
    void approveInstructionsSkipsWhenSubmitFails() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var drafts = mapper.readTree("""
                [{"instruction_id":"I-1","owning_lob":"FICC","status":"DRAFT",
                  "created_by":{"user_id":"mo-100","title":"Analyst","supervisor_id":"mo-050"}}]
                """);
        when(instructionClient.listInstructions(any(), eq("DRAFT"), anyInt()))
                .thenReturn(new ServiceResponse(200, drafts.toString(), drafts));
        when(instructionClient.listInstructions(any(), eq("SUBMITTED"), anyInt()))
                .thenReturn(new ServiceResponse(200, "[]", mapper.createArrayNode()));
        when(instructionClient.submitInstruction(any(), eq("I-1")))
                .thenReturn(new ServiceResponse(403, "denied", mapper.readTree("{\"detail\":\"denied\"}")));

        var result = actions.approveInstructions(1, new SessionCredentials("s", "t"));

        assertThat(result.failed()).isEqualTo(1);
    }

    @Test
    void suspendInstructionsHandlesEmptyPool() {
        when(instructionClient.listInstructions(any(), eq("APPROVED"), anyInt()))
                .thenReturn(new ServiceResponse(200, "[]", new ObjectMapper().createArrayNode()));

        var result = actions.suspendInstructions(2, new SessionCredentials("s", "t"));

        assertThat(result.succeeded()).isZero();
        assertThat(result.logs()).anyMatch(line -> line.contains("No APPROVED instructions"));
    }

    @Test
    void createPaymentsSkipsUnknownCreator() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var approved = mapper.readTree("""
                [{"instruction_id":"I-1","owning_lob":"FICC","instruction_type":"STANDING","status":"APPROVED"}]
                """);
        when(instructionClient.listInstructions(any(), eq("APPROVED"), anyInt()))
                .thenReturn(new ServiceResponse(200, approved.toString(), approved));

        SeedFile seedWithUnknown = new com.srecatalog.harness.seed.SeedFile(
                HarnessTestFixtures.SAMPLE_SEED.defaults(),
                HarnessTestFixtures.SAMPLE_SEED.users());
        when(seedFileLoader.load()).thenReturn(seedWithUnknown);

        HarnessProperties paymentProperties = new HarnessProperties(
                "http://localhost:9000", "/api/v1", "http://localhost:9093", "/api/v1",
                "classpath:users.yaml", "Password1!", "ssi.local", "admin-001",
                "security_events", "instruction_service", "payment_service", false);
        HarnessHelpers helpersWithBadPlan = new HarnessHelpers(
                paymentProperties, loginClient, instructionClient, paymentClient) {
            @Override
            public java.util.List<java.util.Map.Entry<String, Double>> buildPaymentSeedPlan(
                    int count, com.srecatalog.harness.seed.SeedFile seed) {
                return java.util.List.of(java.util.Map.entry("unknown-user", 1_000_000.0));
            }
        };
        actions = new HarnessActions(
                paymentProperties,
                seedFileLoader, helpersWithBadPlan, instructionClient, paymentClient, securityEventCounter);

        var result = actions.createPayments(1, new SessionCredentials("s", "t"));

        assertThat(result.failed()).isEqualTo(1);
    }

    @Test
    void updatePaymentsSkipsMissingCreatorAndUnchangedAmount() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var payments = mapper.readTree("""
                [{"payment_id":"P-1","instruction_id":"I-1","value_date":"2026-07-01","amount":1000000,
                  "created_by":{}},
                 {"payment_id":"P-2","instruction_id":"I-2","value_date":"2026-07-01","amount":1000000,
                  "created_by":{"user_id":"pay-101"}}]
                """);
        when(paymentClient.listPayments(any(), eq("DRAFT"), anyInt()))
                .thenReturn(new ServiceResponse(200, payments.toString(), payments));

        HarnessHelpers helpersFixedAmount = new HarnessHelpers(
                new HarnessProperties(
                        "http://localhost:9000", "/api/v1", "http://localhost:9093", "/api/v1",
                        "classpath:users.yaml", "Password1!", "ssi.local", "admin-001",
                        "security_events", "instruction_service", "payment_service", false),
                loginClient, instructionClient, paymentClient) {
            @Override
            public double resolvePaymentUpdateAmount(
                    double currentAmount, String creatorUserId,
                    com.srecatalog.harness.seed.SeedFile seed, Double override) {
                return currentAmount;
            }
        };
        actions = new HarnessActions(
                new HarnessProperties(
                        "http://localhost:9000", "/api/v1", "http://localhost:9093", "/api/v1",
                        "classpath:users.yaml", "Password1!", "ssi.local", "admin-001",
                        "security_events", "instruction_service", "payment_service", false),
                seedFileLoader, helpersFixedAmount, instructionClient, paymentClient, securityEventCounter);

        var result = actions.updatePayments(2, new SessionCredentials("s", "t"), null);

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
    }

    @Test
    void submitPaymentsHandlesMissingSubmitter() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var draft = mapper.readTree("""
                [{"payment_id":"P-1","owning_lob":"UNKNOWN","status":"DRAFT","amount":1000000,
                  "created_by":{"user_id":"pay-101"}}]
                """);
        when(paymentClient.listPayments(any(), eq("DRAFT"), anyInt()))
                .thenReturn(new ServiceResponse(200, draft.toString(), draft));

        var result = actions.submitPayments(1, new SessionCredentials("s", "t"));

        assertThat(result.failed()).isEqualTo(1);
    }

    @Test
    void approvePaymentsSkipsWhenNoApprover() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var submitted = mapper.readTree("""
                [{"payment_id":"P-9","owning_lob":"UNKNOWN","status":"SUBMITTED","amount":1000000,
                  "created_by":{"user_id":"pay-101","title":"Analyst"}}]
                """);
        when(paymentClient.listPayments(any(), eq("SUBMITTED"), anyInt()))
                .thenReturn(new ServiceResponse(200, submitted.toString(), submitted));

        var result = actions.approvePayments(1, new SessionCredentials("s", "t"));

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
    }

    @Test
    void rejectPaymentsSkipsWhenNoRejector() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var submitted = mapper.readTree("""
                [{"payment_id":"P-9","owning_lob":"UNKNOWN","status":"SUBMITTED","amount":1000000,
                  "created_by":{"user_id":"pay-101"}}]
                """);
        when(paymentClient.listPayments(any(), eq("SUBMITTED"), anyInt()))
                .thenReturn(new ServiceResponse(200, submitted.toString(), submitted));

        var result = actions.rejectPayments(1, new SessionCredentials("s", "t"));

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
    }

    @Test
    void runPolicyScenarioExecutesReadAndListSteps() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var created = mapper.readTree("{\"instruction_id\":\"I-1\"}");
        var listed = mapper.readTree("[{\"instruction_id\":\"I-1\"}]");
        when(instructionClient.createInstruction(any(), any(Map.class)))
                .thenReturn(new ServiceResponse(201, created.toString(), created));
        when(instructionClient.getInstruction(any(), eq("I-1")))
                .thenReturn(new ServiceResponse(200, created.toString(), created));
        when(instructionClient.listInstructions(any(), any(), anyInt()))
                .thenReturn(new ServiceResponse(200, listed.toString(), listed));
        when(instructionClient.submitInstruction(any(), eq("I-1")))
                .thenReturn(new ServiceResponse(200, "{}", mapper.createObjectNode()));
        when(instructionClient.approveInstruction(any(), eq("I-1")))
                .thenReturn(new ServiceResponse(200, "{\"status\":\"APPROVED\"}", mapper.readTree("{\"status\":\"APPROVED\"}")));
        when(instructionClient.listVersions(any(), eq("I-1")))
                .thenReturn(new ServiceResponse(200, "[]", mapper.createArrayNode()));

        var result = actions.runPolicyScenario(new SessionCredentials("s", "t"));

        assertThat(result.logs()).anyMatch(line -> line.contains("PASS"));
    }

    @Test
    void runPaymentPolicyScenarioVerifiesSecurityEvents() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var approved = mapper.readTree("""
                [{"instruction_id":"I-1","owning_lob":"FICC","instruction_type":"STANDING","status":"APPROVED"}]
                """);
        var payment = mapper.readTree("{\"payment_id\":\"P-1\"}");
        when(instructionClient.listInstructions(any(), eq("APPROVED"), anyInt()))
                .thenReturn(new ServiceResponse(200, approved.toString(), approved));
        when(paymentClient.createPayment(any(), eq("I-1"), anyDouble(), anyString()))
                .thenReturn(new ServiceResponse(201, payment.toString(), payment));
        when(paymentClient.submitPayment(any(), eq("P-1")))
                .thenReturn(new ServiceResponse(200, "{}", mapper.createObjectNode()));
        when(paymentClient.approvePayment(any(), eq("P-1")))
                .thenReturn(new ServiceResponse(200, "{}", mapper.createObjectNode()));
        when(paymentClient.rejectPayment(any(), eq("P-1"), anyString()))
                .thenReturn(new ServiceResponse(200, "{}", mapper.createObjectNode()));
        when(securityEventCounter.countPaymentEvents("ALERT", "failure")).thenReturn(2L, 5L);
        when(securityEventCounter.countPaymentEvents("INFO", "success")).thenReturn(1L, 4L);

        HarnessProperties properties = new HarnessProperties(
                "http://localhost:9000", "/api/v1", "http://localhost:9093", "/api/v1",
                "classpath:users.yaml", "Password1!", "ssi.local", "admin-001",
                "security_events", "instruction_service", "payment_service", true);
        HarnessHelpers helpers = new HarnessHelpers(properties, loginClient, instructionClient, paymentClient);
        actions = new HarnessActions(properties, seedFileLoader, helpers, instructionClient, paymentClient, securityEventCounter);

        var result = actions.runPaymentPolicyScenario(new SessionCredentials("s", "t"));

        assertThat(result.logs()).anyMatch(line -> line.contains("Security events"));
    }

    @Test
    void approveInstructionsApprovesAlreadySubmitted() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var submitted = mapper.readTree("""
                [{"instruction_id":"I-1","owning_lob":"FICC","status":"SUBMITTED",
                  "created_by":{"user_id":"mo-100","title":"Analyst","supervisor_id":"mo-050"}}]
                """);
        when(instructionClient.listInstructions(any(), eq("DRAFT"), anyInt()))
                .thenReturn(new ServiceResponse(200, "[]", mapper.createArrayNode()));
        when(instructionClient.listInstructions(any(), eq("SUBMITTED"), anyInt()))
                .thenReturn(new ServiceResponse(200, submitted.toString(), submitted));
        when(instructionClient.approveInstruction(any(), eq("I-1")))
                .thenReturn(new ServiceResponse(200, "{\"status\":\"APPROVED\"}", mapper.readTree("{\"status\":\"APPROVED\"}")));

        var result = actions.approveInstructions(1, new SessionCredentials("s", "t"));

        assertThat(result.succeeded()).isEqualTo(1);
    }

    @Test
    void reactivateInstructionsRecordsFailure() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var suspended = mapper.readTree("[{\"instruction_id\":\"I-1\",\"owning_lob\":\"FICC\",\"status\":\"SUSPENDED\"}]");
        when(instructionClient.listInstructions(any(), eq("SUSPENDED"), anyInt()))
                .thenReturn(new ServiceResponse(200, suspended.toString(), suspended));
        when(instructionClient.reactivateInstruction(any(), eq("I-1")))
                .thenReturn(new ServiceResponse(403, "denied", mapper.readTree("{\"detail\":\"denied\"}")));

        var result = actions.reactivateInstructions(1, new SessionCredentials("s", "t"));

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.ok()).isFalse();
    }

    @Test
    void createPaymentsUsesApprovedPoolWhenNoStandingInstructions() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var approved = mapper.readTree("""
                [{"instruction_id":"I-1","owning_lob":"FICC","instruction_type":"SINGLE_USE","status":"APPROVED"}]
                """);
        var payment = mapper.readTree("{\"payment_id\":\"P-1\"}");
        when(instructionClient.listInstructions(any(), eq("APPROVED"), anyInt()))
                .thenReturn(new ServiceResponse(200, approved.toString(), approved));
        when(paymentClient.createPayment(any(), eq("I-1"), anyDouble(), anyString()))
                .thenReturn(new ServiceResponse(201, payment.toString(), payment));

        var result = actions.createPayments(1, new SessionCredentials("s", "t"));

        assertThat(result.succeeded()).isEqualTo(1);
    }

    @Test
    void runPaymentPolicyScenarioFailsWhenSecurityEventsMissing() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var approved = mapper.readTree("""
                [{"instruction_id":"I-1","owning_lob":"FICC","instruction_type":"STANDING","status":"APPROVED"}]
                """);
        var payment = mapper.readTree("{\"payment_id\":\"P-1\"}");
        when(instructionClient.listInstructions(any(), eq("APPROVED"), anyInt()))
                .thenReturn(new ServiceResponse(200, approved.toString(), approved));
        when(paymentClient.createPayment(any(), eq("I-1"), anyDouble(), anyString()))
                .thenReturn(new ServiceResponse(201, payment.toString(), payment));
        when(paymentClient.submitPayment(any(), eq("P-1")))
                .thenReturn(new ServiceResponse(200, "{}", mapper.createObjectNode()));
        when(paymentClient.approvePayment(any(), eq("P-1")))
                .thenReturn(new ServiceResponse(200, "{}", mapper.createObjectNode()));
        when(paymentClient.rejectPayment(any(), eq("P-1"), anyString()))
                .thenReturn(new ServiceResponse(200, "{}", mapper.createObjectNode()));
        when(securityEventCounter.countPaymentEvents("ALERT", "failure")).thenReturn(5L, 5L);
        when(securityEventCounter.countPaymentEvents("INFO", "success")).thenReturn(3L, 3L);

        HarnessProperties properties = new HarnessProperties(
                "http://localhost:9000", "/api/v1", "http://localhost:9093", "/api/v1",
                "classpath:users.yaml", "Password1!", "ssi.local", "admin-001",
                "security_events", "instruction_service", "payment_service", true);
        HarnessHelpers helpers = new HarnessHelpers(properties, loginClient, instructionClient, paymentClient);
        actions = new HarnessActions(properties, seedFileLoader, helpers, instructionClient, paymentClient, securityEventCounter);

        var result = actions.runPaymentPolicyScenario(new SessionCredentials("s", "t"));

        assertThat(result.ok()).isFalse();
        assertThat(result.logs()).anyMatch(line -> line.contains("expected an ALERT security event"));
    }

    @Test
    void rejectInstructionsHandlesEmptySubmittedList() {
        when(instructionClient.listInstructions(any(), eq("SUBMITTED"), anyInt()))
                .thenReturn(new ServiceResponse(200, "[]", new ObjectMapper().createArrayNode()));

        var result = actions.rejectInstructions(2, new SessionCredentials("s", "t"));

        assertThat(result.succeeded()).isZero();
        assertThat(result.logs()).anyMatch(line -> line.contains("No SUBMITTED instructions"));
    }

    @Test
    void runPolicyScenarioSkipsWhenCreateFails() {
        when(instructionClient.createInstruction(any(), any(Map.class)))
                .thenReturn(new ServiceResponse(403, "denied", null));

        var result = actions.runPolicyScenario(new SessionCredentials("s", "t"));

        assertThat(result.logs()).anyMatch(line -> line.contains("skip: no instruction_id"));
    }

    @Test
    void createPaymentsSkipsWhenCreatorHasNoMatchingInstruction() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var approved = mapper.readTree("""
                [{"instruction_id":"I-1","owning_lob":"DESK_RATES","instruction_type":"STANDING","status":"APPROVED"}]
                """);
        when(instructionClient.listInstructions(any(), eq("APPROVED"), anyInt()))
                .thenReturn(new ServiceResponse(200, approved.toString(), approved));

        var result = actions.createPayments(1, new SessionCredentials("s", "t"));

        assertThat(result.failed()).isEqualTo(1);
    }
}
