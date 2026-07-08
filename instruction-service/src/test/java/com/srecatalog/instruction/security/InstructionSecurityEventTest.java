package com.srecatalog.instruction.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.srecatalog.instruction.InstructionTestFixtures;
import com.srecatalog.instruction.model.InstructionAction;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InstructionSecurityEventTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @Test
    void authorizedActionIncludesSnapshot() {
        var event = InstructionSecurityEvent.authorizedAction(
                InstructionAction.CREATE,
                InstructionTestFixtures.CREATOR,
                InstructionTestFixtures.sampleInstruction("I-1"),
                1,
                Map.of("authorization", Map.of("summary", "allowed")),
                objectMapper);
        assertThat(event.severity()).isEqualTo(com.srecatalog.instruction.model.SecurityEventSeverity.INFO);
        assertThat(event.instructionSnapshot()).containsKey("instruction_id");
    }

    @Test
    void policyDenialMarksAlert() {
        var event = InstructionSecurityEvent.policyDenial(
                InstructionAction.APPROVE,
                InstructionTestFixtures.CREATOR,
                InstructionTestFixtures.sampleInstruction("I-1"),
                "denied",
                Map.of(),
                objectMapper);
        assertThat(event.severity()).isEqualTo(com.srecatalog.instruction.model.SecurityEventSeverity.ALERT);
    }
}
