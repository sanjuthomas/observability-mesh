package com.observabilitymesh.instruction.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.observabilitymesh.instruction.InstructionTestFixtures;
import com.observabilitymesh.instruction.model.InstructionAction;
import com.observabilitymesh.instruction.model.SecurityEventOutcome;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InstructionSecurityEventAllActionsTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @Test
    void contextTypesVaryByAction() {
        var create = InstructionSecurityEvent.authorizedAction(
                InstructionAction.CREATE, InstructionTestFixtures.CREATOR,
                InstructionTestFixtures.sampleInstruction("I-1"), 1, null, objectMapper);
        var cancel = InstructionSecurityEvent.authorizedAction(
                InstructionAction.CANCEL, InstructionTestFixtures.CREATOR,
                InstructionTestFixtures.sampleInstruction("I-1"), 1, null, objectMapper);
        var view = InstructionSecurityEvent.authorizedAction(
                InstructionAction.VIEW, InstructionTestFixtures.CREATOR,
                InstructionTestFixtures.sampleInstruction("I-1"), 1, null, objectMapper);
        var change = InstructionSecurityEvent.authorizedAction(
                InstructionAction.SUBMIT, InstructionTestFixtures.CREATOR,
                InstructionTestFixtures.sampleInstruction("I-1"), 1, null, objectMapper);

        assertThat(create.event().type()).contains("creation");
        assertThat(cancel.event().type()).contains("cancellation");
        assertThat(view.event().type()).contains("access");
        assertThat(change.event().type()).contains("change");
        assertThat(create.event().outcome()).isEqualTo(SecurityEventOutcome.SUCCESS.value());
    }
}
