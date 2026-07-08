package com.observabilitymesh.instruction.service;

import com.observabilitymesh.instruction.InstructionTestFixtures;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubjectAccessTest {

    @Test
    void platformAdminCanViewAnyInstruction() {
        assertThat(SubjectAccess.canViewInstruction(
                InstructionTestFixtures.ADMIN,
                InstructionTestFixtures.sampleInstruction("I-1"))).isTrue();
    }

    @Test
    void creatorCanViewOwnInstruction() {
        assertThat(SubjectAccess.canViewInstruction(
                InstructionTestFixtures.CREATOR,
                InstructionTestFixtures.sampleInstruction("I-1"))).isTrue();
    }

    @Test
    void requireComplianceRejectsNonComplianceRole() {
        assertThatThrownBy(() -> SubjectAccess.requireCompliance(
                InstructionTestFixtures.CREATOR, InstructionTestFixtures.properties()))
                .isInstanceOf(com.observabilitymesh.common.web.PermissionDeniedException.class);
    }
}
