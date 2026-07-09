package com.observabilitymesh.payment.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.observabilitymesh.common.model.UserReference;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Payment {

    private String paymentId;
    private String instructionId;
    private int instructionVersion;
    private PaymentStatus status = PaymentStatus.DRAFT;
    private double amount;
    private String currency;
    private String valueDate;
    private String owningLob;
    private String instructionType;
    private UserReference createdBy;
    private UserReference submittedBy;
    private UserReference approvedBy;
    private UserReference rejectedBy;
    private UserReference cancelledBy;
    private String rejectionReason;
    private String cancellationReason;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant submittedAt;
    private Instant approvedAt;
    private Instant rejectedAt;
    private Instant cancelledAt;
    private List<LifecycleEvent> lifecycleEvents = new ArrayList<>();

    public static Payment create(
            String paymentId,
            String instructionId,
            int instructionVersion,
            double amount,
            String currency,
            String valueDate,
            String owningLob,
            String instructionType,
            UserReference createdBy,
            String eventId) {
        Payment payment = new Payment();
        Instant now = Instant.now();
        payment.paymentId = paymentId;
        payment.instructionId = instructionId;
        payment.instructionVersion = instructionVersion;
        payment.amount = amount;
        payment.currency = currency;
        payment.valueDate = valueDate;
        payment.owningLob = owningLob;
        payment.instructionType = instructionType;
        payment.createdBy = createdBy;
        payment.createdAt = now;
        payment.updatedAt = now;
        payment.lifecycleEvents.add(new LifecycleEvent(
                eventId,
                PaymentAction.CREATE.name(),
                createdBy.userId(),
                now.toString(),
                null));
        return payment;
    }

    public Payment copy() {
        Payment copy = new Payment();
        copy.paymentId = paymentId;
        copy.instructionId = instructionId;
        copy.instructionVersion = instructionVersion;
        copy.status = status;
        copy.amount = amount;
        copy.currency = currency;
        copy.valueDate = valueDate;
        copy.owningLob = owningLob;
        copy.instructionType = instructionType;
        copy.createdBy = createdBy;
        copy.submittedBy = submittedBy;
        copy.approvedBy = approvedBy;
        copy.rejectedBy = rejectedBy;
        copy.cancelledBy = cancelledBy;
        copy.rejectionReason = rejectionReason;
        copy.cancellationReason = cancellationReason;
        copy.createdAt = createdAt;
        copy.updatedAt = updatedAt;
        copy.submittedAt = submittedAt;
        copy.approvedAt = approvedAt;
        copy.rejectedAt = rejectedAt;
        copy.cancelledAt = cancelledAt;
        copy.lifecycleEvents.addAll(lifecycleEvents);
        return copy;
    }

    public void addLifecycleEvent(LifecycleEvent event) {
        lifecycleEvents.add(event);
    }

    public java.util.Map<String, Object> toOpaPayment(String instructionEndDate, String instructionStatus) {
        java.util.Map<String, Object> createdByMap = new java.util.LinkedHashMap<>();
        createdByMap.put("user_id", createdBy.userId());
        createdByMap.put("supervisor_id", createdBy.supervisorId());

        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("payment_id", paymentId);
        map.put("instruction_id", instructionId);
        map.put("instruction_version", instructionVersion);
        map.put("status", status.name());
        map.put("amount", amount);
        map.put("currency", currency);
        map.put("instruction_status", instructionStatus);
        map.put("instruction_end_date", instructionEndDate);
        map.put("instruction_type", instructionType);
        map.put("instruction_owning_lob", owningLob);
        map.put("created_by", createdByMap);
        return map;
    }

    public String paymentId() { return paymentId; }
    public String instructionId() { return instructionId; }
    public int instructionVersion() { return instructionVersion; }
    public PaymentStatus status() { return status; }
    public double amount() { return amount; }
    public String currency() { return currency; }
    public String valueDate() { return valueDate; }
    public String owningLob() { return owningLob; }
    public String instructionType() { return instructionType; }
    public UserReference createdBy() { return createdBy; }
    public UserReference submittedBy() { return submittedBy; }
    public UserReference approvedBy() { return approvedBy; }
    public UserReference rejectedBy() { return rejectedBy; }
    public UserReference cancelledBy() { return cancelledBy; }
    public String rejectionReason() { return rejectionReason; }
    public String cancellationReason() { return cancellationReason; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public Instant submittedAt() { return submittedAt; }
    public Instant approvedAt() { return approvedAt; }
    public Instant rejectedAt() { return rejectedAt; }
    public Instant cancelledAt() { return cancelledAt; }
    public List<LifecycleEvent> lifecycleEvents() { return List.copyOf(lifecycleEvents); }

    public void setInstructionVersion(int instructionVersion) { this.instructionVersion = instructionVersion; }
    public void setAmount(double amount) { this.amount = amount; }
    public void setValueDate(String valueDate) { this.valueDate = valueDate; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void setStatus(PaymentStatus status) { this.status = status; }
    public void setSubmittedBy(UserReference submittedBy) { this.submittedBy = submittedBy; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
    public void setApprovedBy(UserReference approvedBy) { this.approvedBy = approvedBy; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
    public void setRejectedBy(UserReference rejectedBy) { this.rejectedBy = rejectedBy; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public void setRejectedAt(Instant rejectedAt) { this.rejectedAt = rejectedAt; }
    public void setCancelledBy(UserReference cancelledBy) { this.cancelledBy = cancelledBy; }
    public void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }

    public static String newEventId() {
        return UUID.randomUUID().toString();
    }
}
