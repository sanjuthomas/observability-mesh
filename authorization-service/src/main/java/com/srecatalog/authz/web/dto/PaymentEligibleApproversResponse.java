package com.srecatalog.authz.web.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PaymentEligibleApproversResponse(
        String paymentId,
        String instructionId,
        String paymentStatus,
        double amount,
        String currency,
        String owningLob,
        String instructionStatus,
        String evaluatedAt,
        List<EligibleApprover> eligible,
        List<EligibleApprover> prospectiveEligible,
        int candidatesEvaluated,
        String approvalBlockedReason
) {
}
