package com.observabilitymesh.instruction.model;

import com.observabilitymesh.instruction.InstructionTestFixtures;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CashSettlementInstructionTest {

    @Test
    void copyPreservesIdentity() {
        CashSettlementInstruction original = InstructionTestFixtures.sampleInstruction("I-1");
        original.addLifecycleEvent(new LifecycleEvent("e1", "CREATE", "user-001", "now", Map.of()));
        CashSettlementInstruction copy = original.copy();
        assertThat(copy.instructionId()).isEqualTo("I-1");
        assertThat(copy.lifecycleEvents()).hasSize(1);
    }

    @Test
    void toOpaInstructionIncludesStatusAndType() {
        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        Map<String, Object> opa = instruction.toOpaInstruction();
        assertThat(opa.get("status")).isEqualTo("DRAFT");
        assertThat(opa.get("type")).isEqualTo("STANDING");
    }

    @Test
    void toOpaAccountReturnsFundingLob() {
        assertThat(InstructionTestFixtures.sampleInstruction("I-1").toOpaAccount())
                .containsEntry("owning_lob", "FICC");
    }
}
