package com.observabilitymesh.instruction.service;

import com.observabilitymesh.instruction.InstructionTestFixtures;
import com.observabilitymesh.instruction.model.InstructionStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SubjectAccessExtendedTest {

    @Test
    void approverCoversInstructionLob() {
        assertThat(SubjectAccess.canViewInstruction(
                InstructionTestFixtures.APPROVER,
                InstructionTestFixtures.sampleInstruction("I-1"))).isTrue();
    }

    @Test
    void platformAdminPassesCompliance() {
        SubjectAccess.requireCompliance(InstructionTestFixtures.ADMIN, InstructionTestFixtures.properties());
    }

    @Test
    void requirePlatformAdminRejectsNonAdmin() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        SubjectAccess.requirePlatformAdmin(InstructionTestFixtures.CREATOR))
                .isInstanceOf(com.observabilitymesh.common.web.PermissionDeniedException.class);
    }
}
