package com.observabilitymesh.instruction.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.observabilitymesh.common.model.UserReference;
import com.observabilitymesh.instruction.model.iso.InstructionIsoTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CashSettlementInstruction {

    private String instructionId;
    private InstructionType instructionType;
    private InstructionStatus status = InstructionStatus.DRAFT;
    private String owningLob;
    private WireScope wireScope;
    private String currency;
    private InstructionIsoTypes.FundingAccount fundingAccount;
    private InstructionIsoTypes.PartyIdentification initiatingParty;
    private InstructionIsoTypes.PartyIdentification ultimateDebtor;
    private InstructionIsoTypes.PartyIdentification debtor;
    private InstructionIsoTypes.CashAccount debtorAccount;
    private InstructionIsoTypes.BranchAndFinancialInstitutionIdentification debtorAgent;
    private InstructionIsoTypes.CashAccount debtorAgentAccount;
    private InstructionIsoTypes.BranchAndFinancialInstitutionIdentification instructingAgent;
    private InstructionIsoTypes.BranchAndFinancialInstitutionIdentification instructedAgent;
    private List<InstructionIsoTypes.AgentWithAccount> previousInstructingAgents = new ArrayList<>();
    private List<InstructionIsoTypes.AgentWithAccount> intermediaryAgents = new ArrayList<>();
    private InstructionIsoTypes.BranchAndFinancialInstitutionIdentification creditorAgent;
    private InstructionIsoTypes.CashAccount creditorAgentAccount;
    private InstructionIsoTypes.PartyIdentification creditor;
    private InstructionIsoTypes.CashAccount creditorAccount;
    private InstructionIsoTypes.PartyIdentification ultimateCreditor;
    private ChargeBearer chargeBearer;
    private List<InstructionIsoTypes.InstructionForAgent> instructionsForCreditorAgent = new ArrayList<>();
    private List<InstructionIsoTypes.InstructionForAgent> instructionsForNextAgent = new ArrayList<>();
    private Instant effectiveDate;
    private Instant endDate;
    private UserReference createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant submittedAt;
    private UserReference approvedBy;
    private Instant approvedAt;
    private UserReference rejectedBy;
    private Instant rejectedAt;
    private String rejectionReason;
    private Instant cancelledAt;
    private String suspendedBy;
    private Instant suspendedAt;
    private Instant lastUsedAt;
    private int usageCount;
    private String usedBy;
    private List<LifecycleEvent> lifecycleEvents = new ArrayList<>();

    public CashSettlementInstruction copy() {
        CashSettlementInstruction copy = new CashSettlementInstruction();
        copy.instructionId = instructionId;
        copy.instructionType = instructionType;
        copy.status = status;
        copy.owningLob = owningLob;
        copy.wireScope = wireScope;
        copy.currency = currency;
        copy.fundingAccount = fundingAccount;
        copy.initiatingParty = initiatingParty;
        copy.ultimateDebtor = ultimateDebtor;
        copy.debtor = debtor;
        copy.debtorAccount = debtorAccount;
        copy.debtorAgent = debtorAgent;
        copy.debtorAgentAccount = debtorAgentAccount;
        copy.instructingAgent = instructingAgent;
        copy.instructedAgent = instructedAgent;
        copy.previousInstructingAgents = new ArrayList<>(previousInstructingAgents);
        copy.intermediaryAgents = new ArrayList<>(intermediaryAgents);
        copy.creditorAgent = creditorAgent;
        copy.creditorAgentAccount = creditorAgentAccount;
        copy.creditor = creditor;
        copy.creditorAccount = creditorAccount;
        copy.ultimateCreditor = ultimateCreditor;
        copy.chargeBearer = chargeBearer;
        copy.instructionsForCreditorAgent = new ArrayList<>(instructionsForCreditorAgent);
        copy.instructionsForNextAgent = new ArrayList<>(instructionsForNextAgent);
        copy.effectiveDate = effectiveDate;
        copy.endDate = endDate;
        copy.createdBy = createdBy;
        copy.createdAt = createdAt;
        copy.updatedAt = updatedAt;
        copy.submittedAt = submittedAt;
        copy.approvedBy = approvedBy;
        copy.approvedAt = approvedAt;
        copy.rejectedBy = rejectedBy;
        copy.rejectedAt = rejectedAt;
        copy.rejectionReason = rejectionReason;
        copy.cancelledAt = cancelledAt;
        copy.suspendedBy = suspendedBy;
        copy.suspendedAt = suspendedAt;
        copy.lastUsedAt = lastUsedAt;
        copy.usageCount = usageCount;
        copy.usedBy = usedBy;
        copy.lifecycleEvents.addAll(lifecycleEvents);
        return copy;
    }

    public void addLifecycleEvent(LifecycleEvent event) {
        lifecycleEvents.add(event);
    }

    public Map<String, Object> toOpaInstruction() {
        Map<String, Object> createdByMap = new LinkedHashMap<>();
        createdByMap.put("user_id", createdBy.userId());
        createdByMap.put("title", createdBy.title());
        createdByMap.put("supervisor_id", createdBy.supervisorId());

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", status.name());
        map.put("type", instructionType.name());
        map.put("owning_lob", owningLob);
        map.put("effective_date", effectiveDate == null ? "" : effectiveDate.toString());
        map.put("end_date", endDate == null ? "" : endDate.toString());
        map.put("created_by", createdByMap);
        map.put("suspended_by", suspendedBy);
        map.put("used_by", usedBy);
        return map;
    }

    public Map<String, Object> toOpaAccount() {
        return Map.of("owning_lob", fundingAccount.owningLob());
    }

    public static String newEventId() {
        return UUID.randomUUID().toString();
    }

    public String instructionId() { return instructionId; }
    public InstructionType instructionType() { return instructionType; }
    public InstructionStatus status() { return status; }
    public String owningLob() { return owningLob; }
    public WireScope wireScope() { return wireScope; }
    public String currency() { return currency; }
    public InstructionIsoTypes.FundingAccount fundingAccount() { return fundingAccount; }
    public InstructionIsoTypes.PartyIdentification initiatingParty() { return initiatingParty; }
    public InstructionIsoTypes.PartyIdentification ultimateDebtor() { return ultimateDebtor; }
    public InstructionIsoTypes.PartyIdentification debtor() { return debtor; }
    public InstructionIsoTypes.CashAccount debtorAccount() { return debtorAccount; }
    public InstructionIsoTypes.BranchAndFinancialInstitutionIdentification debtorAgent() { return debtorAgent; }
    public InstructionIsoTypes.CashAccount debtorAgentAccount() { return debtorAgentAccount; }
    public InstructionIsoTypes.BranchAndFinancialInstitutionIdentification instructingAgent() { return instructingAgent; }
    public InstructionIsoTypes.BranchAndFinancialInstitutionIdentification instructedAgent() { return instructedAgent; }
    public List<InstructionIsoTypes.AgentWithAccount> previousInstructingAgents() { return List.copyOf(previousInstructingAgents); }
    public List<InstructionIsoTypes.AgentWithAccount> intermediaryAgents() { return List.copyOf(intermediaryAgents); }
    public InstructionIsoTypes.BranchAndFinancialInstitutionIdentification creditorAgent() { return creditorAgent; }
    public InstructionIsoTypes.CashAccount creditorAgentAccount() { return creditorAgentAccount; }
    public InstructionIsoTypes.PartyIdentification creditor() { return creditor; }
    public InstructionIsoTypes.CashAccount creditorAccount() { return creditorAccount; }
    public InstructionIsoTypes.PartyIdentification ultimateCreditor() { return ultimateCreditor; }
    public ChargeBearer chargeBearer() { return chargeBearer; }
    public List<InstructionIsoTypes.InstructionForAgent> instructionsForCreditorAgent() { return List.copyOf(instructionsForCreditorAgent); }
    public List<InstructionIsoTypes.InstructionForAgent> instructionsForNextAgent() { return List.copyOf(instructionsForNextAgent); }
    public Instant effectiveDate() { return effectiveDate; }
    public Instant endDate() { return endDate; }
    public UserReference createdBy() { return createdBy; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public Instant submittedAt() { return submittedAt; }
    public UserReference approvedBy() { return approvedBy; }
    public Instant approvedAt() { return approvedAt; }
    public UserReference rejectedBy() { return rejectedBy; }
    public Instant rejectedAt() { return rejectedAt; }
    public String rejectionReason() { return rejectionReason; }
    public Instant cancelledAt() { return cancelledAt; }
    public String suspendedBy() { return suspendedBy; }
    public Instant suspendedAt() { return suspendedAt; }
    public Instant lastUsedAt() { return lastUsedAt; }
    public int usageCount() { return usageCount; }
    public String usedBy() { return usedBy; }
    public List<LifecycleEvent> lifecycleEvents() { return List.copyOf(lifecycleEvents); }

    public void setInstructionId(String instructionId) { this.instructionId = instructionId; }
    public void setInstructionType(InstructionType instructionType) { this.instructionType = instructionType; }
    public void setStatus(InstructionStatus status) { this.status = status; }
    public void setOwningLob(String owningLob) { this.owningLob = owningLob; }
    public void setWireScope(WireScope wireScope) { this.wireScope = wireScope; }
    public void setCurrency(String currency) { this.currency = currency; }
    public void setFundingAccount(InstructionIsoTypes.FundingAccount fundingAccount) { this.fundingAccount = fundingAccount; }
    public void setInitiatingParty(InstructionIsoTypes.PartyIdentification initiatingParty) { this.initiatingParty = initiatingParty; }
    public void setUltimateDebtor(InstructionIsoTypes.PartyIdentification ultimateDebtor) { this.ultimateDebtor = ultimateDebtor; }
    public void setDebtor(InstructionIsoTypes.PartyIdentification debtor) { this.debtor = debtor; }
    public void setDebtorAccount(InstructionIsoTypes.CashAccount debtorAccount) { this.debtorAccount = debtorAccount; }
    public void setDebtorAgent(InstructionIsoTypes.BranchAndFinancialInstitutionIdentification debtorAgent) { this.debtorAgent = debtorAgent; }
    public void setDebtorAgentAccount(InstructionIsoTypes.CashAccount debtorAgentAccount) { this.debtorAgentAccount = debtorAgentAccount; }
    public void setInstructingAgent(InstructionIsoTypes.BranchAndFinancialInstitutionIdentification instructingAgent) { this.instructingAgent = instructingAgent; }
    public void setInstructedAgent(InstructionIsoTypes.BranchAndFinancialInstitutionIdentification instructedAgent) { this.instructedAgent = instructedAgent; }
    public void setPreviousInstructingAgents(List<InstructionIsoTypes.AgentWithAccount> previousInstructingAgents) { this.previousInstructingAgents = new ArrayList<>(previousInstructingAgents); }
    public void setIntermediaryAgents(List<InstructionIsoTypes.AgentWithAccount> intermediaryAgents) { this.intermediaryAgents = new ArrayList<>(intermediaryAgents); }
    public void setCreditorAgent(InstructionIsoTypes.BranchAndFinancialInstitutionIdentification creditorAgent) { this.creditorAgent = creditorAgent; }
    public void setCreditorAgentAccount(InstructionIsoTypes.CashAccount creditorAgentAccount) { this.creditorAgentAccount = creditorAgentAccount; }
    public void setCreditor(InstructionIsoTypes.PartyIdentification creditor) { this.creditor = creditor; }
    public void setCreditorAccount(InstructionIsoTypes.CashAccount creditorAccount) { this.creditorAccount = creditorAccount; }
    public void setUltimateCreditor(InstructionIsoTypes.PartyIdentification ultimateCreditor) { this.ultimateCreditor = ultimateCreditor; }
    public void setChargeBearer(ChargeBearer chargeBearer) { this.chargeBearer = chargeBearer; }
    public void setInstructionsForCreditorAgent(List<InstructionIsoTypes.InstructionForAgent> instructionsForCreditorAgent) { this.instructionsForCreditorAgent = new ArrayList<>(instructionsForCreditorAgent); }
    public void setInstructionsForNextAgent(List<InstructionIsoTypes.InstructionForAgent> instructionsForNextAgent) { this.instructionsForNextAgent = new ArrayList<>(instructionsForNextAgent); }
    public void setEffectiveDate(Instant effectiveDate) { this.effectiveDate = effectiveDate; }
    public void setEndDate(Instant endDate) { this.endDate = endDate; }
    public void setCreatedBy(UserReference createdBy) { this.createdBy = createdBy; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
    public void setApprovedBy(UserReference approvedBy) { this.approvedBy = approvedBy; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
    public void setRejectedBy(UserReference rejectedBy) { this.rejectedBy = rejectedBy; }
    public void setRejectedAt(Instant rejectedAt) { this.rejectedAt = rejectedAt; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }
    public void setSuspendedBy(String suspendedBy) { this.suspendedBy = suspendedBy; }
    public void setSuspendedAt(Instant suspendedAt) { this.suspendedAt = suspendedAt; }
    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }
    public void setUsageCount(int usageCount) { this.usageCount = usageCount; }
    public void setUsedBy(String usedBy) { this.usedBy = usedBy; }
    public void setLifecycleEvents(List<LifecycleEvent> lifecycleEvents) { this.lifecycleEvents = new ArrayList<>(lifecycleEvents); }
}
