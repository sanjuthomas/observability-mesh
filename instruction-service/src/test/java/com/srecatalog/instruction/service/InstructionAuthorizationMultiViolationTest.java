package com.srecatalog.instruction.service;

import com.srecatalog.common.model.PolicyDecision;
import com.srecatalog.common.model.Subject;
import com.srecatalog.instruction.InstructionTestFixtures;
import com.srecatalog.instruction.model.InstructionAction;
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
