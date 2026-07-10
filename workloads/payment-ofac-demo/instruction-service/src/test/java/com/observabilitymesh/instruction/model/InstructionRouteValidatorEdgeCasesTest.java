package com.observabilitymesh.instruction.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InstructionRouteValidatorEdgeCasesTest {

    @Test
    void rejectsInvalidOwningLob() {
        CashSettlementInstruction instruction = com.observabilitymesh.instruction.InstructionTestFixtures.sampleInstruction("I-1");
        assertThatCode(() -> InstructionRouteValidator.validate(instruction)).doesNotThrowAnyException();
        instruction.setOwningLob("INVALID");
        assertThatThrownBy(() -> InstructionRouteValidator.validate(instruction))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
