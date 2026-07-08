package com.srecatalog.harness.service;

import com.srecatalog.auth.KeycloakLoginClient;
import com.srecatalog.harness.HarnessTestFixtures;
import com.srecatalog.harness.client.InstructionServiceClient;
import com.srecatalog.harness.client.PaymentServiceClient;
import com.srecatalog.harness.client.ServiceResponse;
import com.srecatalog.harness.config.HarnessProperties;
import com.srecatalog.harness.seed.SeedFile;
import com.srecatalog.harness.seed.SeedUser;
import com.srecatalog.harness.model.SessionCredentials;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HarnessHelpersTest {

    @Mock KeycloakLoginClient loginClient;
    @Mock InstructionServiceClient instructionClient;
    @Mock PaymentServiceClient paymentClient;

    private HarnessHelpers helpers;

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
                true);
        helpers = new HarnessHelpers(properties, loginClient, instructionClient, paymentClient);
    }

    @Test
    void buildScenarioContainsPolicyDenials() {
        assertThat(helpers.buildScenario())
                .anyMatch(step -> step.userId().equals("ficc-201") && !step.expectSuccess());
    }

    @Test
    void sessionForUserUsesEmailLogin() {
        when(loginClient.login("mo-100@ssi.local", "Password1!"))
                .thenReturn(new KeycloakLoginClient.LoginResponse("mo-100@ssi.local", "token", "sess"));

        SessionCredentials session = helpers.sessionForUser(HarnessTestFixtures.SAMPLE_SEED, "mo-100");

        assertThat(session.sessionToken()).isEqualTo("token");
        assertThat(session.sessionId()).isEqualTo("sess");
    }

    @Test
    void approverForInstructionSelectsEligibleVp() {
        String approver = helpers.approverForInstruction(
                HarnessTestFixtures.SAMPLE_SEED, "FICC", "mo-100", "Analyst", "mo-050");

        assertThat(approver).isEqualTo("ficc-300");
    }

    @Test
    void paymentSubmitterForLobReturnsFrontOfficeUser() {
        assertThat(helpers.paymentSubmitterForLob(HarnessTestFixtures.SAMPLE_SEED, "FICC"))
                .isEqualTo("fo-ficc-101");
    }

    @Test
    void resolvePaymentUpdateAmountUsesOverride() {
        double amount = helpers.resolvePaymentUpdateAmount(1_000_000.0, "pay-101", HarnessTestFixtures.SAMPLE_SEED, 2_000_000.0);
        assertThat(amount).isEqualTo(2_000_000.0);
    }

    @Test
    void fetchInstructionsParsesArrayPayload() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var json = mapper.readTree("[{\"instruction_id\":\"I-1\",\"status\":\"DRAFT\"}]");
        when(instructionClient.listInstructions(any(), eq("DRAFT"), eq(500)))
                .thenReturn(new ServiceResponse(200, json.toString(), json));

        var instructions = helpers.fetchInstructions(new SessionCredentials("s", "t"), "DRAFT");

        assertThat(instructions).hasSize(1);
        assertThat(instructions.getFirst().path("instruction_id").asText()).isEqualTo("I-1");
    }

    @Test
    void buildPaymentScenarioContainsExpectedDenials() {
        assertThat(helpers.buildPaymentScenario())
                .anyMatch(step -> step.operation() == HarnessHelpers.PaymentOperation.CREATE_PAYMENT
                        && step.userId().equals("pay-201")
                        && !step.expectSuccess());
    }

    @Test
    void buildSeedPlanReturnsRowsForCreators() {
        assertThat(helpers.buildSeedPlan(3, HarnessTestFixtures.SAMPLE_SEED)).hasSize(3);
    }

    @Test
    void buildPaymentSeedPlanUsesCreators() {
        assertThat(helpers.buildPaymentSeedPlan(2, HarnessTestFixtures.SAMPLE_SEED)).hasSize(2);
    }

    @Test
    void approverForPaymentSelectsVp() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var payment = mapper.readTree("""
                {"payment_id":"P-1","owning_lob":"FICC","amount":1000000,
                 "created_by":{"user_id":"pay-101","title":"Analyst"}}
                """);
        assertThat(helpers.approverForPayment(HarnessTestFixtures.SAMPLE_SEED, payment)).isEqualTo("pay-201");
    }

    @Test
    void fetchPaymentsParsesItemsArray() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var json = mapper.readTree("{\"items\":[{\"payment_id\":\"P-1\"}]}");
        when(paymentClient.listPayments(any(), eq("DRAFT"), eq(500)))
                .thenReturn(new ServiceResponse(200, json.toString(), json));

        assertThat(helpers.fetchPayments(new SessionCredentials("s", "t"), "DRAFT"))
                .extracting(node -> node.path("payment_id").asText())
                .containsExactly("P-1");
    }

    @Test
    void fetchInstructionsReturnsEmptyOnFailure() {
        when(instructionClient.listInstructions(any(), any(), anyInt()))
                .thenReturn(new ServiceResponse(500, "error", null));

        assertThat(helpers.fetchInstructions(new SessionCredentials("s", "t"), "DRAFT")).isEmpty();
    }

    @Test
    void rejectorForPaymentReturnsFundingApprover() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var payment = mapper.readTree("""
                {"payment_id":"P-1","owning_lob":"FICC","amount":1000000,
                 "created_by":{"user_id":"pay-101","title":"Analyst"}}
                """);

        assertThat(helpers.rejectorForPayment(HarnessTestFixtures.SAMPLE_SEED, payment)).isEqualTo("pay-201");
    }

    @Test
    void instructionSubmitterFallsBackToDefault() {
        SeedFile emptyCreators = new SeedFile(
                HarnessTestFixtures.SAMPLE_SEED.defaults(),
                HarnessTestFixtures.SAMPLE_SEED.users().stream()
                        .filter(user -> !user.roles().contains("INSTRUCTION_CREATOR"))
                        .toList());

        assertThat(helpers.instructionSubmitter(emptyCreators)).isEqualTo("mo-100");
    }

    @Test
    void resolvePaymentUpdateAmountBumpsWhenNoOverride() {
        double bumped = helpers.resolvePaymentUpdateAmount(1_000_000.0, "pay-101", HarnessTestFixtures.SAMPLE_SEED, null);

        assertThat(bumped).isGreaterThan(1_000_000.0);
    }

    @Test
    void instructionApprovalSortKeyPrefersResolvableApprovers() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var withApprover = mapper.readTree("""
                {"instruction_id":"I-2","owning_lob":"FICC",
                 "created_by":{"user_id":"mo-100","title":"Analyst","supervisor_id":"mo-050"}}
                """);
        var withoutApprover = mapper.readTree("""
                {"instruction_id":"I-1","owning_lob":"UNKNOWN",
                 "created_by":{"user_id":"mo-100","title":"Analyst"}}
                """);

        var sorted = java.util.List.of(withoutApprover, withApprover).stream()
                .sorted(helpers.instructionApprovalSortKey(HarnessTestFixtures.SAMPLE_SEED))
                .toList();

        assertThat(sorted.getFirst().path("instruction_id").asText()).isEqualTo("I-2");
    }

    @Test
    void paymentApprovalSortKeyPrefersResolvableApprovers() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var withApprover = mapper.readTree("""
                {"payment_id":"P-2","owning_lob":"FICC","amount":1000000,
                 "created_by":{"user_id":"pay-101","title":"Analyst"}}
                """);
        var withoutApprover = mapper.readTree("""
                {"payment_id":"P-1","owning_lob":"UNKNOWN","amount":1000000,
                 "created_by":{"user_id":"pay-101","title":"Analyst"}}
                """);

        var sorted = java.util.List.of(withoutApprover, withApprover).stream()
                .sorted(helpers.paymentApprovalSortKey(HarnessTestFixtures.SAMPLE_SEED))
                .toList();

        assertThat(sorted.getFirst().path("payment_id").asText()).isEqualTo("P-2");
    }

    @Test
    void buildSeedPlanUsesFallbackPairsWhenNoCreators() {
        SeedFile emptyCreators = new SeedFile(
                HarnessTestFixtures.SAMPLE_SEED.defaults(),
                HarnessTestFixtures.SAMPLE_SEED.users().stream()
                        .filter(user -> !user.roles().contains("INSTRUCTION_CREATOR"))
                        .toList());

        assertThat(helpers.buildSeedPlan(2, emptyCreators)).hasSize(2);
    }

    @Test
    void buildPaymentSeedPlanUsesFallbackWhenNoCreators() {
        SeedFile noPaymentCreators = new SeedFile(
                HarnessTestFixtures.SAMPLE_SEED.defaults(),
                HarnessTestFixtures.SAMPLE_SEED.users().stream()
                        .filter(user -> !user.roles().contains("PAYMENT_CREATOR"))
                        .toList());

        assertThat(helpers.buildPaymentSeedPlan(2, noPaymentCreators))
                .extracting(Map.Entry::getKey)
                .contains("pay-101");
    }

    @Test
    void approverForInstructionReturnsNullWhenNoEligibleApprover() {
        assertThat(helpers.approverForInstruction(
                HarnessTestFixtures.SAMPLE_SEED, "UNKNOWN", "mo-100", "Analyst", null))
                .isNull();
    }

    @Test
    void approverForInstructionSkipsCreatorAndSupervisor() {
        SeedFile seed = new SeedFile(
                HarnessTestFixtures.SAMPLE_SEED.defaults(),
                List.of(
                        new SeedUser(
                                "mo-100", "Sarah", "Chen", "Analyst",
                                List.of("INSTRUCTION_CREATOR"), List.of("MIDDLE_OFFICE"),
                                null, "ficc-300", List.of()),
                        new SeedUser(
                                "ficc-300", "Elena", "Vasquez", "Vice President",
                                List.of("INSTRUCTION_APPROVER"), List.of(),
                                "FICC", null, List.of())));

        assertThat(helpers.approverForInstruction(seed, "FICC", "mo-100", "Analyst", "ficc-300"))
                .isNull();
    }

    @Test
    void approverForPaymentSkipsWhenAmountExceedsClubLimit() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var payment = mapper.readTree("""
                {"payment_id":"P-1","owning_lob":"FICC","amount":2000000000,
                 "created_by":{"user_id":"pay-101","title":"Analyst"}}
                """);

        assertThat(helpers.approverForPayment(HarnessTestFixtures.SAMPLE_SEED, payment)).isNull();
    }

    @Test
    void resolvePaymentUpdateAmountReturnsCurrentWhenBumpExceedsCap() {
        SeedFile cappedCreator = new SeedFile(
                HarnessTestFixtures.SAMPLE_SEED.defaults(),
                List.of(new SeedUser(
                        "pay-101", "Amy", "Nguyen", "Analyst",
                        List.of("PAYMENT_CREATOR"), List.of("MIDDLE_OFFICE", "UP_TO_100_MILLION_CLUB"),
                        null, null, List.of("FICC"))));

        double amount = helpers.resolvePaymentUpdateAmount(100_000_000.0, "pay-101", cappedCreator, null);

        assertThat(amount).isEqualTo(100_000_000.0);
    }

    @Test
    void sessionForUserUsesEmptySessionIdWhenMissing() {
        when(loginClient.login("mo-100@ssi.local", "Password1!"))
                .thenReturn(new KeycloakLoginClient.LoginResponse("mo-100@ssi.local", "token", null));

        SessionCredentials session = helpers.sessionForUser(HarnessTestFixtures.SAMPLE_SEED, "mo-100");

        assertThat(session.sessionId()).isEmpty();
    }
}
