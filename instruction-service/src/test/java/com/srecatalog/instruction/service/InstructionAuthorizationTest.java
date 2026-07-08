package com.srecatalog.instruction.service;

import com.srecatalog.common.model.PolicyDecision;
import com.srecatalog.common.model.Subject;
import com.srecatalog.instruction.InstructionTestFixtures;
import com.srecatalog.instruction.model.InstructionAction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InstructionAuthorizationTest {

    @Test
    void buildAuthorizationBlockAllowSummary() {
        Subject subject = InstructionTestFixtures.CREATOR;
        Map<String, Object> block = InstructionAuthorization.buildAuthorizationBlock(
                PolicyDecision.allow(List.of("creator title eligible")),
                subject,
                InstructionAction.CREATE,
                Map.of("instruction_id", "I-1"));
        assertThat(block.get("decision")).isEqualTo("allow");
        assertThat(String.valueOf(block.get("summary"))).contains("allowed");
    }

    @Test
    void buildAuthorizationBlockDenySummary() {
        Subject subject = InstructionTestFixtures.CREATOR;
        Map<String, Object> block = InstructionAuthorization.buildAuthorizationBlock(
                PolicyDecision.deny(List.of("SELF_APPROVAL"), true),
                subject,
                InstructionAction.APPROVE,
                Map.of());
        assertThat(block.get("decision")).isEqualTo("deny");
        assertThat(String.valueOf(block.get("summary"))).contains("denied");
    }

    @Test
    void detailsWithAuthorizationMerges() {
        Map<String, Object> merged = InstructionAuthorization.detailsWithAuthorization(
                Map.of("reason", "x"), Map.of("decision", "allow"));
        assertThat(merged).containsKey("authorization");
        assertThat(merged.get("reason")).isEqualTo("x");
    }
}
