package com.srecatalog.payment.security;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.srecatalog.common.model.Subject;
import com.srecatalog.payment.model.Payment;
import com.srecatalog.payment.model.PaymentAction;
import com.srecatalog.payment.model.SecurityEventOutcome;
import com.srecatalog.payment.model.SecurityEventSeverity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record PaymentSecurityEvent(
        Instant timestamp,
        SecurityEventSeverity severity,
        String message,
        SecurityEventContext event,
        SecurityEventActor actor,
        SecurityEventResource resource,
        SecurityEventSource source,
        Map<String, Object> details,
        @JsonProperty("payment_snapshot") Map<String, Object> paymentSnapshot
) {
    public static PaymentSecurityEvent authorizedAction(
            PaymentAction action,
            Subject subject,
            Payment payment,
            Integer versionNumber,
            Map<String, Object> details) {
        Map<String, Object> eventDetails = details == null ? Map.of() : Map.copyOf(details);
        String reason = null;
        if (eventDetails.get("authorization") instanceof Map<?, ?> auth) {
            Object summary = auth.get("summary");
            if (summary != null) {
                reason = String.valueOf(summary);
            }
        }
        return new PaymentSecurityEvent(
                Instant.now(),
                SecurityEventSeverity.INFO,
                "Authorized " + action.name() + " on payment " + payment.paymentId() + " by " + subject.userId(),
                SecurityEventContext.forAction(action, SecurityEventOutcome.SUCCESS, reason),
                SecurityEventActor.from(subject),
                SecurityEventResource.from(payment, versionNumber),
                SecurityEventSource.paymentService(),
                eventDetails,
                paymentSnapshot(payment));
    }

    public static PaymentSecurityEvent policyDenial(
            PaymentAction action,
            Subject subject,
            Payment payment,
            String reason,
            Map<String, Object> details,
            SecurityEventSeverity severity) {
        Map<String, Object> eventDetails = details == null ? new java.util.LinkedHashMap<>() : new java.util.LinkedHashMap<>(details);
        eventDetails.put("policy_engine", "opa");
        return new PaymentSecurityEvent(
                Instant.now(),
                severity == null ? SecurityEventSeverity.ALERT : severity,
                "Policy denied " + action.name() + " on payment " + payment.paymentId() + " by " + subject.userId(),
                SecurityEventContext.forAction(action, SecurityEventOutcome.FAILURE, reason),
                SecurityEventActor.from(subject),
                SecurityEventResource.from(payment, null),
                SecurityEventSource.paymentService(),
                eventDetails,
                paymentSnapshot(payment));
    }

    private static Map<String, Object> paymentSnapshot(Payment payment) {
        return Map.of(
                "payment_id", payment.paymentId(),
                "instruction_id", payment.instructionId(),
                "status", payment.status().name(),
                "amount", payment.amount(),
                "currency", payment.currency());
    }

    public record SecurityEventActor(
            @JsonProperty("user_id") String userId,
            @JsonProperty("given_name") String givenName,
            @JsonProperty("family_name") String familyName,
            String title,
            List<String> roles,
            List<String> groups,
            @JsonProperty("covering_lobs") List<String> coveringLobs,
            String lob,
            @JsonProperty("supervisor_id") String supervisorId
    ) {
        static SecurityEventActor from(Subject subject) {
            return new SecurityEventActor(
                    subject.userId(),
                    subject.givenName(),
                    subject.familyName(),
                    subject.title(),
                    subject.roles(),
                    subject.groups(),
                    subject.coveringLobs(),
                    subject.lob(),
                    subject.supervisorId());
        }
    }

    public record SecurityEventResource(
            String type,
            String id,
            @JsonProperty("instruction_id") String instructionId,
            @JsonProperty("owning_lob") String owningLob,
            String status,
            double amount,
            String currency,
            @JsonProperty("version_number") Integer versionNumber
    ) {
        static SecurityEventResource from(Payment payment, Integer versionNumber) {
            return new SecurityEventResource(
                    "cash_payment",
                    payment.paymentId(),
                    payment.instructionId(),
                    payment.owningLob(),
                    payment.status().name(),
                    payment.amount(),
                    payment.currency(),
                    versionNumber);
        }
    }

    public record SecurityEventContext(
            String kind,
            List<String> category,
            List<String> type,
            String action,
            String outcome,
            String reason
    ) {
        static SecurityEventContext forAction(PaymentAction action, SecurityEventOutcome outcome, String reason) {
            List<String> types = switch (action) {
                case CREATE -> List.of("creation");
                case CANCEL -> List.of("cancellation");
                default -> List.of("change");
            };
            if (outcome == SecurityEventOutcome.FAILURE) {
                types = List.of("access", "denied");
            }
            return new SecurityEventContext("event", List.of("iam"), types, action.name(), outcome.value(), reason);
        }
    }

    public record SecurityEventSource(String application, String service, String version) {
        static SecurityEventSource paymentService() {
            return new SecurityEventSource("payment-service", "payment-service", "0.1.0");
        }
    }
}
