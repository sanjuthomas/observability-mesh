package com.srecatalog.payment.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record PaymentEligibleApproversResponse(
        @JsonProperty("payment_id") String paymentId,
        @JsonProperty("instruction_id") String instructionId,
        @JsonProperty("payment_status") String paymentStatus,
        double amount,
        String currency,
        @JsonProperty("owning_lob") String owningLob,
        @JsonProperty("instruction_status") String instructionStatus,
        @JsonProperty("evaluated_at") String evaluatedAt,
        List<Map<String, Object>> eligible,
        @JsonProperty("prospective_eligible") List<Map<String, Object>> prospectiveEligible,
        @JsonProperty("candidates_evaluated") int candidatesEvaluated,
        @JsonProperty("approval_blocked_reason") String approvalBlockedReason
) {
}
