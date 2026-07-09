package com.observabilitymesh.instruction.web;

import com.observabilitymesh.instruction.model.InstructionConstants;
import com.observabilitymesh.instruction.model.VersionedInstruction;
import com.observabilitymesh.instruction.InstructionTestFixtures;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class InstructionResponseMapperBranchesTest {

    @Test
    void mapsClosedVersionOutTimestamp() {
        var instruction = InstructionTestFixtures.sampleInstruction("I-1");
        Instant closed = Instant.parse("2026-06-01T00:00:00Z");
        var response = InstructionResponseMapper.toResponse(
                new VersionedInstruction(instruction, 1, Instant.parse("2026-05-01T00:00:00Z"), closed));
        assertThat(response.recordOut()).isEqualTo(closed.toString());
        assertThat(response.recordOut()).isNotEqualTo(InstructionConstants.CURRENT_OUT);
    }

    @Test
    void mapsNullInstantsToEmptyOrNullStrings() {
        var instruction = InstructionTestFixtures.sampleInstruction("I-1");
        instruction.setSubmittedAt(null);
        instruction.setApprovedAt(null);
        var response = InstructionResponseMapper.toResponse(
                new VersionedInstruction(instruction, 1, null, null));
        assertThat(response.recordIn()).isEmpty();
        assertThat(response.recordOut()).isEqualTo(InstructionConstants.CURRENT_OUT);
        assertThat(response.submittedAt()).isNull();
    }
}
