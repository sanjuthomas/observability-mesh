package com.srecatalog.instruction.web.dto;

import com.srecatalog.instruction.model.ChargeBearer;
import com.srecatalog.instruction.model.InstructionType;
import com.srecatalog.instruction.model.WireScope;
import com.srecatalog.instruction.model.iso.InstructionIsoTypes;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CreateInstructionRequestTest {

    @Test
    void compactConstructorDefaultsNullLists() {
        CreateInstructionRequest request = new CreateInstructionRequest(
                InstructionType.STANDING,
                "FICC",
                WireScope.DOMESTIC,
                "USD",
                new InstructionIsoTypes.FundingAccount("FA-1", null, "FICC"),
                null,
                null,
                new InstructionIsoTypes.PartyIdentification("D", null, null, "US"),
                new InstructionIsoTypes.CashAccount(
                        com.srecatalog.instruction.model.AccountIdentificationScheme.IBAN, "1", null, null),
                new InstructionIsoTypes.BranchAndFinancialInstitutionIdentification(
                        new InstructionIsoTypes.FinancialInstitutionIdentification(
                                com.srecatalog.instruction.model.FinancialInstitutionIdScheme.BICFI, "BIC", null, null),
                        null,
                        "US"),
                null,
                null,
                null,
                null,
                null,
                new InstructionIsoTypes.BranchAndFinancialInstitutionIdentification(
                        new InstructionIsoTypes.FinancialInstitutionIdentification(
                                com.srecatalog.instruction.model.FinancialInstitutionIdScheme.BICFI, "BIC2", null, null),
                        null,
                        "US"),
                null,
                new InstructionIsoTypes.PartyIdentification("C", null, null, "US"),
                new InstructionIsoTypes.CashAccount(
                        com.srecatalog.instruction.model.AccountIdentificationScheme.IBAN, "2", null, null),
                null,
                ChargeBearer.DEBT,
                null,
                null,
                Instant.now(),
                Instant.now().plusSeconds(3600));
        assertThat(request.previousInstructingAgents()).isEmpty();
        assertThat(request.intermediaryAgents()).isEmpty();
        assertThat(request.instructionsForCreditorAgent()).isEmpty();
    }
}
