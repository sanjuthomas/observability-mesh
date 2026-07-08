package com.observabilitymesh.instruction.model;

import com.observabilitymesh.instruction.InstructionTestFixtures;
import com.observabilitymesh.instruction.model.iso.InstructionIsoTypes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;

class InstructionRouteValidatorFullInternationalTest {

    @Test
    void acceptsInternationalRouteWithAgentChains() {
        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        instruction.setWireScope(WireScope.INTERNATIONAL);
        instruction.setDebtorAgent(bicfi("DEUTDEFF"));
        instruction.setCreditorAgent(bicfi("CHASUS33"));
        instruction.setInstructingAgent(bicfi("IRVTUS3N"));
        instruction.setInstructedAgent(bicfi("CITIUS33"));
        instruction.setIntermediaryAgents(List.of(new InstructionIsoTypes.AgentWithAccount(bicfi("MIDBUS33"), null)));
        instruction.setPreviousInstructingAgents(List.of(new InstructionIsoTypes.AgentWithAccount(bicfi("PRVSUS33"), null)));
        instruction.setCreditorAccount(new InstructionIsoTypes.CashAccount(
                AccountIdentificationScheme.BBAN, "123456", "USD", null));
        assertThatCode(() -> InstructionRouteValidator.validate(instruction)).doesNotThrowAnyException();
    }

    private static InstructionIsoTypes.BranchAndFinancialInstitutionIdentification bicfi(String code) {
        return new InstructionIsoTypes.BranchAndFinancialInstitutionIdentification(
                new InstructionIsoTypes.FinancialInstitutionIdentification(
                        FinancialInstitutionIdScheme.BICFI, code, "Bank", null),
                "Bank",
                "US");
    }
}
