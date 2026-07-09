package com.observabilitymesh.instruction.service;

import com.observabilitymesh.common.model.PolicyDecision;
import com.observabilitymesh.common.model.Subject;
import com.observabilitymesh.instruction.InstructionTestFixtures;
import com.observabilitymesh.instruction.model.InstructionAction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InstructionAuthorizationAllowBasisTest {

    @Test
    void allowWithoutBasisUsesShortSummary() {
        Subject subject = InstructionTestFixtures.CREATOR;
        Map<String, Object> block = InstructionAuthorization.buildAuthorizationBlock(
                PolicyDecision.allow(List.of()),
                subject,
                InstructionAction.SUBMIT,
                Map.of());
        assertThat(String.valueOf(block.get("summary"))).isEqualTo(subject.displayName() + " was allowed to SUBMIT");
    }

    @Test
    void denyWithEmptyViolationsUsesPolicyDeniedLabel() {
        Map<String, Object> block = InstructionAuthorization.buildAuthorizationBlock(
                PolicyDecision.deny(List.of(), false),
                InstructionTestFixtures.CREATOR,
                InstructionAction.APPROVE,
                Map.of());
        assertThat(String.valueOf(block.get("summary"))).contains("policy denied");
    }

    @Test
    void denyWithoutAlertPrefixUsesFirstViolation() {
        Map<String, Object> block = InstructionAuthorization.buildAuthorizationBlock(
                PolicyDecision.deny(List.of("SELF_APPROVAL"), false),
                InstructionTestFixtures.CREATOR,
                InstructionAction.APPROVE,
                Map.of());
        assertThat(String.valueOf(block.get("summary"))).contains("creator cannot approve");
    }
}
