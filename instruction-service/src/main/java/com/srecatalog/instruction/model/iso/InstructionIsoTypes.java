package com.srecatalog.instruction.model.iso;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.srecatalog.instruction.model.AccountIdentificationScheme;
import com.srecatalog.instruction.model.FinancialInstitutionIdScheme;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class InstructionIsoTypes {

    private InstructionIsoTypes() {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record FundingAccount(
            @NotBlank @Size(max = 64) String accountId,
            @Size(max = 256) String accountName,
            @NotBlank @Size(max = 64) String owningLob
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record PostalAddress(
            @Size(max = 70) String streetName,
            @Size(max = 16) String buildingNumber,
            @Size(max = 16) String postCode,
            @Size(max = 35) String townName,
            @Size(max = 35) String countrySubDivision,
            @NotBlank @Size(min = 2, max = 2) String country,
            List<@Size(max = 70) String> addressLines
    ) {
        public PostalAddress {
            addressLines = addressLines == null ? List.of() : List.copyOf(addressLines);
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record PartyIdentification(
            @NotBlank @Size(max = 140) String name,
            PostalAddress postalAddress,
            @Size(max = 35) String organisationIdentification,
            @Size(min = 2, max = 2) String countryOfResidence
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CashAccount(
            AccountIdentificationScheme identificationScheme,
            @NotBlank @Size(max = 34) String identification,
            @Size(min = 3, max = 3) String currency,
            @Size(max = 70) String name
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record FinancialInstitutionIdentification(
            FinancialInstitutionIdScheme scheme,
            @NotBlank @Size(max = 35) String identification,
            @Size(max = 140) String name,
            @Size(max = 35) String clearingSystemId
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record BranchAndFinancialInstitutionIdentification(
            FinancialInstitutionIdentification financialInstitution,
            @Size(max = 140) String name,
            @Size(min = 2, max = 2) String country
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AgentWithAccount(
            BranchAndFinancialInstitutionIdentification agent,
            CashAccount account
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record InstructionForAgent(
            @Size(max = 4) String code,
            @Size(max = 140) String instructionInformation
    ) {
    }
}
