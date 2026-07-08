package com.srecatalog.authz.web.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record InstructionEligibleApproversResponse(
        String instructionId,
        String instructionStatus,
        String instructionType,
        String owningLob,
        String createdByUserId,
        String createdByTitle,
        String evaluatedAt,
        List<EligibleApprover> eligible,
        List<EligibleApprover> prospectiveEligible,
        int candidatesEvaluated,
        String approvalBlockedReason
) {
}
