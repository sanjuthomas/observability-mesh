package com.srecatalog.instruction.service;

import com.srecatalog.common.model.Subject;
import com.srecatalog.common.model.UserReference;
import com.srecatalog.instruction.model.CashSettlementInstruction;
import com.srecatalog.instruction.model.InstructionStatus;
import com.srecatalog.instruction.model.InstructionRouteValidator;
import com.srecatalog.instruction.web.dto.CreateInstructionRequest;

import java.time.Instant;

final class InstructionMapper {

    private InstructionMapper() {
    }

    static CashSettlementInstruction fromRequest(
            CreateInstructionRequest request,
            Subject subject,
            String instructionId,
            InstructionStatus status,
            UserReference createdBy) {
        CashSettlementInstruction instruction = new CashSettlementInstruction();
        Instant now = Instant.now();
        instruction.setInstructionId(instructionId);
        instruction.setInstructionType(request.instructionType());
        instruction.setStatus(status);
        instruction.setOwningLob(request.owningLob());
        instruction.setWireScope(request.wireScope());
        instruction.setCurrency(request.currency());
        instruction.setFundingAccount(request.fundingAccount());
        instruction.setInitiatingParty(request.initiatingParty());
        instruction.setUltimateDebtor(request.ultimateDebtor());
        instruction.setDebtor(request.debtor());
        instruction.setDebtorAccount(request.debtorAccount());
        instruction.setDebtorAgent(request.debtorAgent());
        instruction.setDebtorAgentAccount(request.debtorAgentAccount());
        instruction.setInstructingAgent(request.instructingAgent());
        instruction.setInstructedAgent(request.instructedAgent());
        instruction.setPreviousInstructingAgents(request.previousInstructingAgents());
        instruction.setIntermediaryAgents(request.intermediaryAgents());
        instruction.setCreditorAgent(request.creditorAgent());
        instruction.setCreditorAgentAccount(request.creditorAgentAccount());
        instruction.setCreditor(request.creditor());
        instruction.setCreditorAccount(request.creditorAccount());
        instruction.setUltimateCreditor(request.ultimateCreditor());
        instruction.setChargeBearer(request.chargeBearer());
        instruction.setInstructionsForCreditorAgent(request.instructionsForCreditorAgent());
        instruction.setInstructionsForNextAgent(request.instructionsForNextAgent());
        instruction.setEffectiveDate(request.effectiveDate());
        instruction.setEndDate(request.endDate());
        instruction.setCreatedBy(createdBy == null ? InstructionAuthorization.userRef(subject) : createdBy);
        instruction.setCreatedAt(now);
        instruction.setUpdatedAt(now);
        InstructionRouteValidator.validate(instruction);
        return instruction;
    }
}
