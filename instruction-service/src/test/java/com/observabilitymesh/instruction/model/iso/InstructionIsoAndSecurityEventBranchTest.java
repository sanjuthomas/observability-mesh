package com.observabilitymesh.instruction.model.iso;

import com.observabilitymesh.instruction.model.InstructionAction;
import com.observabilitymesh.instruction.security.InstructionSecurityEvent;
import com.observabilitymesh.common.model.PolicyDecision;
import com.observabilitymesh.instruction.InstructionTestFixtures;
import com.observabilitymesh.instruction.model.SecurityEventOutcome;
import com.observabilitymesh.instruction.service.InstructionAuthorization;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InstructionIsoAndSecurityEventBranchTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @Test
    void postalAddressDefaultsNullAddressLines() {
        var address = new InstructionIsoTypes.PostalAddress(
                "Main St", "1", "10001", "NYC", "NY", "US", null);
        assertThat(address.addressLines()).isEmpty();
    }

    @Test
    void instructionForAgentRecordHoldsValues() {
        var forAgent = new InstructionIsoTypes.InstructionForAgent("HOLD", "Wait for funds");
        assertThat(forAgent.code()).isEqualTo("HOLD");
    }

    @Test
    void authorizedActionIncludesAuthorizationSummary() {
        Map<String, Object> auth = InstructionAuthorization.buildAuthorizationBlock(
                PolicyDecision.allow(List.of("creator owns lob")),
                InstructionTestFixtures.CREATOR,
                InstructionAction.CREATE,
                Map.of());
        var event = InstructionSecurityEvent.authorizedAction(
                InstructionAction.CREATE,
                InstructionTestFixtures.CREATOR,
                InstructionTestFixtures.sampleInstruction("I-1"),
                1,
                Map.of("authorization", auth),
                objectMapper);
        assertThat(event.event().reason()).contains("allowed");
        assertThat(event.message()).contains("CREATE");
    }

    @Test
    void authorizedActionIncludesDelegatedSuffix() {
        var delegated = new com.observabilitymesh.common.model.Subject(
                "user-001", "Jane", "Doe", "VP", "FICC",
                List.of("INSTRUCTION_CREATOR"), List.of(), null, List.of("FICC"),
                "svc-instruction", List.of());
        var event = InstructionSecurityEvent.authorizedAction(
                InstructionAction.VIEW,
                delegated,
                InstructionTestFixtures.sampleInstruction("I-1"),
                1,
                null,
                objectMapper);
        assertThat(event.message()).contains("svc-instruction");
    }

    @Test
    void policyDenialWithNullDetailsStillBuildsEvent() {
        var event = InstructionSecurityEvent.policyDenial(
                InstructionAction.CANCEL,
                InstructionTestFixtures.CREATOR,
                InstructionTestFixtures.sampleInstruction("I-1"),
                "denied",
                null,
                objectMapper);
        assertThat(event.severity().name()).isEqualTo("ALERT");
        assertThat(event.event().outcome()).isEqualTo(SecurityEventOutcome.FAILURE.value());
    }

    @Test
    void authorizationAllowWithEmptyBasisUsesGenericSummary() {
        Map<String, Object> block = InstructionAuthorization.buildAuthorizationBlock(
                PolicyDecision.allow(List.of()),
                InstructionTestFixtures.CREATOR,
                InstructionAction.VIEW,
                null);
        assertThat(block.get("summary")).asString().contains("was allowed");
    }

    @Test
    void authorizationDenyWithEmptyViolationsUsesPolicyDenied() {
        Map<String, Object> block = InstructionAuthorization.buildAuthorizationBlock(
                PolicyDecision.deny(List.of(), false),
                InstructionTestFixtures.CREATOR,
                InstructionAction.SUBMIT,
                Map.of());
        assertThat(block.get("summary")).asString().contains("policy denied");
    }
}
