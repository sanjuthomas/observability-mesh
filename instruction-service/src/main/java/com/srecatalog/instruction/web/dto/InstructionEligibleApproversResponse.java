package com.srecatalog.instruction.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record InstructionEligibleApproversResponse(
        @JsonProperty("instruction_id") String instructionId,
        @JsonProperty("instruction_status") String instructionStatus,
        @JsonProperty("instruction_type") String instructionType,
        @JsonProperty("owning_lob") String owningLob,
        @JsonProperty("created_by_user_id") String createdByUserId,
        @JsonProperty("created_by_title") String createdByTitle,
        @JsonProperty("evaluated_at") String evaluatedAt,
        List<Map<String, Object>> eligible,
        @JsonProperty("prospective_eligible") List<Map<String, Object>> prospectiveEligible,
        @JsonProperty("candidates_evaluated") int candidatesEvaluated,
        @JsonProperty("approval_blocked_reason") String approvalBlockedReason
) {
}
