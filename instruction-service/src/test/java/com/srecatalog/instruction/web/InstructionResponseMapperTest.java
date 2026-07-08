package com.srecatalog.instruction.web;

import com.srecatalog.instruction.InstructionTestFixtures;
import com.srecatalog.instruction.model.InstructionConstants;
import com.srecatalog.instruction.model.VersionedInstruction;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class InstructionResponseMapperTest {

    @Test
    void mapsVersionedInstructionToResponse() {
        var instruction = InstructionTestFixtures.sampleInstruction("I-1");
        VersionedInstruction record = new VersionedInstruction(instruction, 2, Instant.parse("2026-01-01T00:00:00Z"), null);
        var response = InstructionResponseMapper.toResponse(record);
        assertThat(response.instructionId()).isEqualTo("I-1");
        assertThat(response.versionNumber()).isEqualTo(2);
        assertThat(response.recordOut()).isEqualTo(InstructionConstants.CURRENT_OUT);
        assertThat(response.status()).isEqualTo("DRAFT");
    }
}
