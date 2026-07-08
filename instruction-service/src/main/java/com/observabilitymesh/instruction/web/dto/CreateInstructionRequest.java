package com.observabilitymesh.instruction.web.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.observabilitymesh.instruction.model.ChargeBearer;
import com.observabilitymesh.instruction.model.InstructionType;
import com.observabilitymesh.instruction.model.WireScope;
import com.observabilitymesh.instruction.model.iso.InstructionIsoTypes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CreateInstructionRequest(
        @NotNull InstructionType instructionType,
        @NotBlank @Size(max = 64) String owningLob,
        @NotNull WireScope wireScope,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @NotNull @Valid InstructionIsoTypes.FundingAccount fundingAccount,
        @Valid InstructionIsoTypes.PartyIdentification initiatingParty,
        @Valid InstructionIsoTypes.PartyIdentification ultimateDebtor,
        @NotNull @Valid InstructionIsoTypes.PartyIdentification debtor,
        @NotNull @Valid InstructionIsoTypes.CashAccount debtorAccount,
        @NotNull @Valid InstructionIsoTypes.BranchAndFinancialInstitutionIdentification debtorAgent,
        @Valid InstructionIsoTypes.CashAccount debtorAgentAccount,
        @Valid InstructionIsoTypes.BranchAndFinancialInstitutionIdentification instructingAgent,
        @Valid InstructionIsoTypes.BranchAndFinancialInstitutionIdentification instructedAgent,
        List<InstructionIsoTypes.AgentWithAccount> previousInstructingAgents,
        List<InstructionIsoTypes.AgentWithAccount> intermediaryAgents,
        @NotNull @Valid InstructionIsoTypes.BranchAndFinancialInstitutionIdentification creditorAgent,
        @Valid InstructionIsoTypes.CashAccount creditorAgentAccount,
        @NotNull @Valid InstructionIsoTypes.PartyIdentification creditor,
        @NotNull @Valid InstructionIsoTypes.CashAccount creditorAccount,
        @Valid InstructionIsoTypes.PartyIdentification ultimateCreditor,
        @NotNull ChargeBearer chargeBearer,
        List<InstructionIsoTypes.InstructionForAgent> instructionsForCreditorAgent,
        List<InstructionIsoTypes.InstructionForAgent> instructionsForNextAgent,
        @NotNull Instant effectiveDate,
        @NotNull Instant endDate
) {
    public CreateInstructionRequest {
        previousInstructingAgents = previousInstructingAgents == null ? List.of() : List.copyOf(previousInstructingAgents);
        intermediaryAgents = intermediaryAgents == null ? List.of() : List.copyOf(intermediaryAgents);
        instructionsForCreditorAgent = instructionsForCreditorAgent == null ? List.of() : List.copyOf(instructionsForCreditorAgent);
        instructionsForNextAgent = instructionsForNextAgent == null ? List.of() : List.copyOf(instructionsForNextAgent);
    }
}
