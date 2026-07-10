package com.observabilitymesh.instruction.model;

import com.observabilitymesh.instruction.InstructionTestFixtures;
import com.observabilitymesh.instruction.model.iso.InstructionIsoTypes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InstructionRouteValidatorInternationalTest {

    @Test
    void rejectsInternationalWithoutBicfi() {
        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        instruction.setWireScope(WireScope.INTERNATIONAL);
        assertThatThrownBy(() -> InstructionRouteValidator.validate(instruction))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BICFI");
    }

    @Test
    void rejectsDomesticClearingSystemWithoutId() {
        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        instruction.setCreditorAgent(new InstructionIsoTypes.BranchAndFinancialInstitutionIdentification(
                new InstructionIsoTypes.FinancialInstitutionIdentification(
                        FinancialInstitutionIdScheme.CLEARING_SYSTEM, "123", "Bank", null),
                "Bank",
                "US"));
        assertThatThrownBy(() -> InstructionRouteValidator.validate(instruction))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clearing_system_id");
    }

    @Test
    void rejectsAgentAccountWithoutAgent() {
        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        instruction.setIntermediaryAgents(List.of(new InstructionIsoTypes.AgentWithAccount(
                null,
                new InstructionIsoTypes.CashAccount(AccountIdentificationScheme.IBAN, "X", "USD", null))));
        assertThatThrownBy(() -> InstructionRouteValidator.validate(instruction))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("account requires agent");
    }
}
