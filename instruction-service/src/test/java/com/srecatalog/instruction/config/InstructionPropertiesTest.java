package com.srecatalog.instruction.config;

import com.srecatalog.instruction.InstructionTestFixtures;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InstructionPropertiesTest {

    @Test
    void splitsComplianceRoles() {
        InstructionProperties properties = InstructionTestFixtures.properties();
        assertThat(properties.complianceRoleSet()).contains("COMPLIANCE_ANALYST");
    }

    @Test
    void splitsExcludedUserIds() {
        InstructionProperties properties = InstructionTestFixtures.properties();
        assertThat(properties.securityEventViewExcludedUserIdSet()).contains("admin-001");
    }
}
