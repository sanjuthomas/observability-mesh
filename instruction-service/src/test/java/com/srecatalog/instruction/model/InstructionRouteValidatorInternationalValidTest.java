package com.srecatalog.instruction.model;

import com.srecatalog.instruction.InstructionTestFixtures;
import com.srecatalog.instruction.model.iso.InstructionIsoTypes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class InstructionRouteValidatorInternationalValidTest {

    @Test
    void acceptsInternationalWithBicfiAgents() {
        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        instruction.setWireScope(WireScope.INTERNATIONAL);
        instruction.setDebtorAgent(bicfiAgent("DEUTDEFF"));
        instruction.setCreditorAgent(bicfiAgent("CHASUS33"));
        instruction.setCreditorAccount(new InstructionIsoTypes.CashAccount(
                AccountIdentificationScheme.IBAN, "DE89370400440532013000", "USD", null));
        assertThatCode(() -> InstructionRouteValidator.validate(instruction)).doesNotThrowAnyException();
    }

    private static InstructionIsoTypes.BranchAndFinancialInstitutionIdentification bicfiAgent(String bic) {
        return new InstructionIsoTypes.BranchAndFinancialInstitutionIdentification(
                new InstructionIsoTypes.FinancialInstitutionIdentification(
                        FinancialInstitutionIdScheme.BICFI, bic, "Bank", null),
                "Bank",
                "US");
    }
}
