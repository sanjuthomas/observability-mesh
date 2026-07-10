package com.observabilitymesh.instruction.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.observabilitymesh.common.model.Subject;
import com.observabilitymesh.instruction.InstructionTestFixtures;
import com.observabilitymesh.instruction.model.InstructionAction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InstructionSecurityEventDelegationTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @Test
    void policyDenialIncludesDelegationDetails() {
        Subject delegated = new Subject(
                "user-001", "Jane", "Doe", "VP", "FICC",
                List.of("INSTRUCTION_CREATOR"), List.of(), null, List.of("FICC"),
                "svc-payment", List.of("INSTRUCTION_MARKER"));
        var event = InstructionSecurityEvent.policyDenial(
                InstructionAction.USE,
                delegated,
                InstructionTestFixtures.sampleInstruction("I-1"),
                "denied",
                Map.of(),
                objectMapper);
        assertThat(event.message()).contains("svc-payment");
        assertThat(event.details()).containsEntry("delegated_by", "svc-payment");
    }
}
