package com.observabilitymesh.instruction.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InstructionConstantsTest {

    @Test
    void documentKeyRoundTrip() {
        assertThat(InstructionConstants.instructionIdFromDocumentKey("I-1|3")).isEqualTo("I-1");
        assertThat(InstructionConstants.documentKey("I-1", 3)).isEqualTo("I-1|3");
    }
}
