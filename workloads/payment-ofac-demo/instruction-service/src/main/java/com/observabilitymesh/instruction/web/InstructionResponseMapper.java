package com.observabilitymesh.instruction.web;

import com.observabilitymesh.instruction.model.InstructionConstants;
import com.observabilitymesh.instruction.model.VersionedInstruction;
import com.observabilitymesh.instruction.web.dto.InstructionResponse;

import java.time.Instant;

public final class InstructionResponseMapper {

    private InstructionResponseMapper() {
    }

    public static InstructionResponse toResponse(VersionedInstruction record) {
        var instruction = record.instruction();
        return new InstructionResponse(
                instruction.instructionId(),
                record.versionNumber(),
                formatInstant(record.validIn()),
                record.validOut() == null ? InstructionConstants.CURRENT_OUT : formatInstant(record.validOut()),
                instruction.instructionType().name(),
                instruction.status().name(),
                instruction.owningLob(),
                instruction.wireScope().name(),
                instruction.currency(),
                instruction.fundingAccount(),
                instruction.initiatingParty(),
                instruction.ultimateDebtor(),
                instruction.debtor(),
                instruction.debtorAccount(),
                instruction.debtorAgent(),
                instruction.debtorAgentAccount(),
                instruction.instructingAgent(),
                instruction.instructedAgent(),
                instruction.previousInstructingAgents(),
                instruction.intermediaryAgents(),
                instruction.creditorAgent(),
                instruction.creditorAgentAccount(),
                instruction.creditor(),
                instruction.creditorAccount(),
                instruction.ultimateCreditor(),
                instruction.chargeBearer().name(),
                instruction.instructionsForCreditorAgent(),
                instruction.instructionsForNextAgent(),
                formatInstant(instruction.effectiveDate()),
                formatInstant(instruction.endDate()),
                instruction.createdBy(),
                formatInstant(instruction.createdAt()),
                formatInstant(instruction.updatedAt()),
                formatNullable(instruction.submittedAt()),
                instruction.approvedBy(),
                formatNullable(instruction.approvedAt()),
                instruction.rejectedBy(),
                formatNullable(instruction.rejectedAt()),
                instruction.rejectionReason(),
                formatNullable(instruction.cancelledAt()),
                instruction.suspendedBy(),
                formatNullable(instruction.suspendedAt()),
                formatNullable(instruction.lastUsedAt()),
                instruction.usageCount(),
                instruction.usedBy(),
                instruction.lifecycleEvents());
    }

    private static String formatInstant(Instant instant) {
        return instant == null ? "" : instant.toString();
    }

    private static String formatNullable(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
