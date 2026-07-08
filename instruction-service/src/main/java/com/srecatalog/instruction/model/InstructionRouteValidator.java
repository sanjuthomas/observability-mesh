package com.srecatalog.instruction.model;

import com.srecatalog.instruction.model.iso.InstructionIsoTypes;

import java.util.regex.Pattern;

public final class InstructionRouteValidator {

    private static final Pattern OWNING_LOB_PATTERN = Pattern.compile("^(FICC|FX|DESK_[A-Z][A-Z0-9_]*)$");

    private InstructionRouteValidator() {
    }

    public static void validate(CashSettlementInstruction instruction) {
        if (!isValidOwningLob(instruction.owningLob())) {
            throw new IllegalArgumentException(
                    "owning_lob must be a P&L profit center: FICC, FX, or DESK_*");
        }
        if (!instruction.fundingAccount().owningLob().equals(instruction.owningLob())) {
            throw new IllegalArgumentException("funding_account.owning_lob must match instruction owning_lob");
        }
        validateAgentAccounts(instruction.previousInstructingAgents(), "previous_instructing_agents");
        validateAgentAccounts(instruction.intermediaryAgents(), "intermediary_agents");

        if (instruction.wireScope() == WireScope.INTERNATIONAL) {
            validateInternational(instruction);
        } else {
            validateDomestic(instruction);
        }
    }

    private static void validateAgentAccounts(
            java.util.List<InstructionIsoTypes.AgentWithAccount> agents,
            String fieldName) {
        for (int index = 0; index < agents.size(); index++) {
            InstructionIsoTypes.AgentWithAccount hop = agents.get(index);
            if (hop.account() != null && hop.agent() == null) {
                throw new IllegalArgumentException(fieldName + "[" + index + "] account requires agent");
            }
        }
    }

    private static void validateInternational(CashSettlementInstruction instruction) {
        java.util.List<InstructionIsoTypes.BranchAndFinancialInstitutionIdentification> agents = new java.util.ArrayList<>();
        agents.add(instruction.debtorAgent());
        agents.add(instruction.creditorAgent());
        instruction.intermediaryAgents().forEach(hop -> agents.add(hop.agent()));
        instruction.previousInstructingAgents().forEach(hop -> agents.add(hop.agent()));
        if (instruction.instructingAgent() != null) {
            agents.add(instruction.instructingAgent());
        }
        if (instruction.instructedAgent() != null) {
            agents.add(instruction.instructedAgent());
        }
        for (InstructionIsoTypes.BranchAndFinancialInstitutionIdentification agent : agents) {
            if (agent.financialInstitution().scheme() != FinancialInstitutionIdScheme.BICFI) {
                throw new IllegalArgumentException(
                        "international wires require BICFI on all agents in the payment chain");
            }
        }
        AccountIdentificationScheme scheme = instruction.creditorAccount().identificationScheme();
        if (scheme != AccountIdentificationScheme.IBAN && scheme != AccountIdentificationScheme.BBAN) {
            throw new IllegalArgumentException("international wires require creditor_account IBAN or BBAN");
        }
    }

    private static void validateDomestic(CashSettlementInstruction instruction) {
        InstructionIsoTypes.FinancialInstitutionIdentification fi =
                instruction.creditorAgent().financialInstitution();
        if (fi.scheme() == FinancialInstitutionIdScheme.CLEARING_SYSTEM
                && (fi.clearingSystemId() == null || fi.clearingSystemId().isBlank())) {
            throw new IllegalArgumentException(
                    "domestic wires with CLEARING_SYSTEM require clearing_system_id");
        }
    }

    public static boolean isValidOwningLob(String value) {
        return value != null && OWNING_LOB_PATTERN.matcher(value).matches();
    }
}
