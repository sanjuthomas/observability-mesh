package com.srecatalog.instruction.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.srecatalog.common.model.UserReference;
import com.srecatalog.instruction.model.LifecycleEvent;
import com.srecatalog.instruction.model.iso.InstructionIsoTypes;

import java.util.List;

public record InstructionResponse(
        @JsonProperty("instruction_id") String instructionId,
        @JsonProperty("version_number") int versionNumber,
        @JsonProperty("in") String recordIn,
        @JsonProperty("out") String recordOut,
        @JsonProperty("instruction_type") String instructionType,
        String status,
        @JsonProperty("owning_lob") String owningLob,
        @JsonProperty("wire_scope") String wireScope,
        String currency,
        @JsonProperty("funding_account") InstructionIsoTypes.FundingAccount fundingAccount,
        @JsonProperty("initiating_party") InstructionIsoTypes.PartyIdentification initiatingParty,
        @JsonProperty("ultimate_debtor") InstructionIsoTypes.PartyIdentification ultimateDebtor,
        InstructionIsoTypes.PartyIdentification debtor,
        @JsonProperty("debtor_account") InstructionIsoTypes.CashAccount debtorAccount,
        @JsonProperty("debtor_agent") InstructionIsoTypes.BranchAndFinancialInstitutionIdentification debtorAgent,
        @JsonProperty("debtor_agent_account") InstructionIsoTypes.CashAccount debtorAgentAccount,
        @JsonProperty("instructing_agent") InstructionIsoTypes.BranchAndFinancialInstitutionIdentification instructingAgent,
        @JsonProperty("instructed_agent") InstructionIsoTypes.BranchAndFinancialInstitutionIdentification instructedAgent,
        @JsonProperty("previous_instructing_agents") List<InstructionIsoTypes.AgentWithAccount> previousInstructingAgents,
        @JsonProperty("intermediary_agents") List<InstructionIsoTypes.AgentWithAccount> intermediaryAgents,
        @JsonProperty("creditor_agent") InstructionIsoTypes.BranchAndFinancialInstitutionIdentification creditorAgent,
        @JsonProperty("creditor_agent_account") InstructionIsoTypes.CashAccount creditorAgentAccount,
        InstructionIsoTypes.PartyIdentification creditor,
        @JsonProperty("creditor_account") InstructionIsoTypes.CashAccount creditorAccount,
        @JsonProperty("ultimate_creditor") InstructionIsoTypes.PartyIdentification ultimateCreditor,
        @JsonProperty("charge_bearer") String chargeBearer,
        @JsonProperty("instructions_for_creditor_agent") List<InstructionIsoTypes.InstructionForAgent> instructionsForCreditorAgent,
        @JsonProperty("instructions_for_next_agent") List<InstructionIsoTypes.InstructionForAgent> instructionsForNextAgent,
        @JsonProperty("effective_date") String effectiveDate,
        @JsonProperty("end_date") String endDate,
        @JsonProperty("created_by") UserReference createdBy,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("updated_at") String updatedAt,
        @JsonProperty("submitted_at") String submittedAt,
        @JsonProperty("approved_by") UserReference approvedBy,
        @JsonProperty("approved_at") String approvedAt,
        @JsonProperty("rejected_by") UserReference rejectedBy,
        @JsonProperty("rejected_at") String rejectedAt,
        @JsonProperty("rejection_reason") String rejectionReason,
        @JsonProperty("cancelled_at") String cancelledAt,
        @JsonProperty("suspended_by") String suspendedBy,
        @JsonProperty("suspended_at") String suspendedAt,
        @JsonProperty("last_used_at") String lastUsedAt,
        @JsonProperty("usage_count") int usageCount,
        @JsonProperty("used_by") String usedBy,
        @JsonProperty("lifecycle_events") List<LifecycleEvent> lifecycleEvents
) {
}
