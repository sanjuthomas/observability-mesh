package com.srecatalog.instruction.model;

import com.srecatalog.instruction.InstructionTestFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InstructionRouteValidatorTest {

    @Test
    void acceptsValidDomesticInstruction() {
        InstructionRouteValidator.validate(InstructionTestFixtures.sampleInstruction("I-1"));
    }

    @Test
    void rejectsMismatchedFundingLob() {
        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        instruction.setFundingAccount(new com.srecatalog.instruction.model.iso.InstructionIsoTypes.FundingAccount(
                "FA-1", "Funding", "FX"));
        assertThatThrownBy(() -> InstructionRouteValidator.validate(instruction))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("owning_lob");
    }

    @Test
    void isValidOwningLobRecognizesDeskCodes() {
        assertThat(InstructionRouteValidator.isValidOwningLob("DESK_RATES")).isTrue();
        assertThat(InstructionRouteValidator.isValidOwningLob("EQUITIES")).isFalse();
        assertThat(InstructionRouteValidator.isValidOwningLob(null)).isFalse();
    }

    @Test
    void rejectsDomesticClearingSystemWithoutId() {
        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        instruction.setCreditorAgent(new com.srecatalog.instruction.model.iso.InstructionIsoTypes.BranchAndFinancialInstitutionIdentification(
                new com.srecatalog.instruction.model.iso.InstructionIsoTypes.FinancialInstitutionIdentification(
                        FinancialInstitutionIdScheme.CLEARING_SYSTEM, "026009593", "Bank", null),
                "Bank",
                "US"));
        assertThatThrownBy(() -> InstructionRouteValidator.validate(instruction))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clearing_system_id");
    }

    @Test
    void rejectsAgentAccountWithoutAgent() {
        CashSettlementInstruction instruction = InstructionTestFixtures.sampleInstruction("I-1");
        instruction.setIntermediaryAgents(List.of(new com.srecatalog.instruction.model.iso.InstructionIsoTypes.AgentWithAccount(
                null,
                new com.srecatalog.instruction.model.iso.InstructionIsoTypes.CashAccount(
                        AccountIdentificationScheme.IBAN, "US999", "USD", null))));
        assertThatThrownBy(() -> InstructionRouteValidator.validate(instruction))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("account requires agent");
    }
}
