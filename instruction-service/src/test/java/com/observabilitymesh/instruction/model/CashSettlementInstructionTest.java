package com.observabilitymesh.instruction.model;

import com.observabilitymesh.instruction.InstructionTestFixtures;
import org.junit.jupiter.api.Test;

import java.time.Instant;
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
    void toOpaInstructionUsesRfc3339DatesWithoutDoubleZuluSuffix() {
        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        instruction.setEffectiveDate(Instant.parse("2026-07-09T00:00:00Z"));
        instruction.setEndDate(Instant.parse("2027-07-09T00:00:00Z"));

        Map<String, Object> opa = instruction.toOpaInstruction();

        assertThat(opa.get("effective_date")).isEqualTo("2026-07-09T00:00:00Z");
        assertThat(opa.get("end_date")).isEqualTo("2027-07-09T00:00:00Z");
    }
}
