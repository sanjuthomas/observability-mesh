package com.observabilitymesh.instruction.service;

import com.observabilitymesh.common.model.PolicyDecision;
import com.observabilitymesh.common.model.Subject;
import com.observabilitymesh.instruction.InstructionTestFixtures;
import com.observabilitymesh.instruction.model.InstructionAction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InstructionAuthorizationMultiViolationTest {

    @Test
    void denialSummaryIncludesSecondaryViolations() {
        Subject subject = InstructionTestFixtures.CREATOR;
        Map<String, Object> block = InstructionAuthorization.buildAuthorizationBlock(
                PolicyDecision.deny(List.of("SELF_APPROVAL", "ALERT_LOB_MISMATCH"), true),
                subject,
                InstructionAction.APPROVE,
                Map.of());
        String summary = String.valueOf(block.get("summary"));
        assertThat(summary).contains("denied");
        assertThat(summary).contains("also:");
    }
}
