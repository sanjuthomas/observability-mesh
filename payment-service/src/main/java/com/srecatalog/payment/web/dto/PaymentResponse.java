package com.srecatalog.payment.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.srecatalog.common.model.UserReference;
import com.srecatalog.payment.model.LifecycleEvent;

import java.util.List;

public record PaymentResponse(
        @JsonProperty("payment_id") String paymentId,
        @JsonProperty("version_number") int versionNumber,
        @JsonProperty("in") String recordIn,
        @JsonProperty("out") String recordOut,
        @JsonProperty("instruction_id") String instructionId,
        @JsonProperty("instruction_version") int instructionVersion,
        String status,
        double amount,
        String currency,
        @JsonProperty("value_date") String valueDate,
        @JsonProperty("owning_lob") String owningLob,
        @JsonProperty("instruction_type") String instructionType,
        @JsonProperty("created_by") UserReference createdBy,
        @JsonProperty("submitted_by") UserReference submittedBy,
        @JsonProperty("approved_by") UserReference approvedBy,
        @JsonProperty("rejected_by") UserReference rejectedBy,
        @JsonProperty("cancelled_by") UserReference cancelledBy,
        @JsonProperty("rejection_reason") String rejectionReason,
        @JsonProperty("cancellation_reason") String cancellationReason,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("updated_at") String updatedAt,
        @JsonProperty("submitted_at") String submittedAt,
        @JsonProperty("approved_at") String approvedAt,
        @JsonProperty("rejected_at") String rejectedAt,
        @JsonProperty("cancelled_at") String cancelledAt,
        @JsonProperty("lifecycle_events") List<LifecycleEvent> lifecycleEvents
) {
}
