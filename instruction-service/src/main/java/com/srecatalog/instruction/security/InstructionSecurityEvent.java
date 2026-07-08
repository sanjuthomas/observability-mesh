package com.srecatalog.instruction.security;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.srecatalog.common.model.Subject;
import com.srecatalog.instruction.model.CashSettlementInstruction;
import com.srecatalog.instruction.model.InstructionAction;
import com.srecatalog.instruction.model.SecurityEventOutcome;
import com.srecatalog.instruction.model.SecurityEventSeverity;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record InstructionSecurityEvent(
        Instant timestamp,
        SecurityEventSeverity severity,
        String message,
        SecurityEventContext event,
        SecurityEventActor actor,
        SecurityEventResource resource,
        SecurityEventSource source,
        Map<String, Object> details,
        @JsonProperty("instruction_snapshot") Map<String, Object> instructionSnapshot
) {
    public static InstructionSecurityEvent authorizedAction(
            InstructionAction action,
            Subject subject,
            CashSettlementInstruction instruction,
            Integer versionNumber,
            Map<String, Object> details,
            ObjectMapper objectMapper) {
        Map<String, Object> eventDetails = details == null ? Map.of() : Map.copyOf(details);
        String reason = null;
        if (eventDetails.get("authorization") instanceof Map<?, ?> auth) {
            Object summary = auth.get("summary");
            if (summary != null) {
                reason = String.valueOf(summary);
            }
        }
        String delegatedSuffix = subject.delegatedBy() != null ? " via " + subject.delegatedBy() : "";
        return new InstructionSecurityEvent(
                Instant.now(),
                SecurityEventSeverity.INFO,
                "Authorized " + action.name() + " on instruction "
                        + instruction.instructionId() + " by " + subject.userId() + delegatedSuffix,
                SecurityEventContext.forAction(action, SecurityEventOutcome.SUCCESS, reason),
                SecurityEventActor.from(subject),
                SecurityEventResource.from(instruction, versionNumber),
                SecurityEventSource.instructionService(),
                eventDetails,
                instructionSnapshot(instruction, objectMapper));
    }

    public static InstructionSecurityEvent policyDenial(
            InstructionAction action,
            Subject subject,
            CashSettlementInstruction instruction,
            String reason,
            Map<String, Object> details,
            ObjectMapper objectMapper) {
        Map<String, Object> eventDetails = details == null ? new LinkedHashMap<>() : new LinkedHashMap<>(details);
        eventDetails.put("policy_engine", "opa");
        if (subject.delegatedBy() != null) {
            eventDetails.put("delegated_by", subject.delegatedBy());
            eventDetails.put("delegation", "on_behalf_of");
        }
        String delegatedSuffix = subject.delegatedBy() != null ? " via " + subject.delegatedBy() : "";
        return new InstructionSecurityEvent(
                Instant.now(),
                SecurityEventSeverity.ALERT,
                "Policy denied " + action.name() + " on instruction "
                        + instruction.instructionId() + " by " + subject.userId() + delegatedSuffix,
                SecurityEventContext.forAction(action, SecurityEventOutcome.FAILURE, reason),
                SecurityEventActor.from(subject),
                SecurityEventResource.from(instruction, null),
                SecurityEventSource.instructionService(),
                eventDetails,
                instructionSnapshot(instruction, objectMapper));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> instructionSnapshot(
            CashSettlementInstruction instruction,
            ObjectMapper objectMapper) {
        return objectMapper.convertValue(instruction, Map.class);
    }

    public record SecurityEventActor(
            @JsonProperty("user_id") String userId,
            @JsonProperty("given_name") String givenName,
            @JsonProperty("family_name") String familyName,
            String title,
            List<String> roles,
            List<String> groups,
            String lob,
            @JsonProperty("supervisor_id") String supervisorId,
            @JsonProperty("delegated_by") String delegatedBy
    ) {
        static SecurityEventActor from(Subject subject) {
            return new SecurityEventActor(
                    subject.userId(),
                    subject.givenName(),
                    subject.familyName(),
                    subject.title(),
                    subject.roles(),
                    subject.groups(),
                    subject.lob(),
                    subject.supervisorId(),
                    subject.delegatedBy());
        }
    }

    public record SecurityEventResource(
            String type,
            String id,
            @JsonProperty("owning_lob") String owningLob,
            String status,
            @JsonProperty("instruction_type") String instructionType,
            @JsonProperty("version_number") Integer versionNumber
    ) {
        static SecurityEventResource from(CashSettlementInstruction instruction, Integer versionNumber) {
            return new SecurityEventResource(
                    "cash_settlement_instruction",
                    instruction.instructionId(),
                    instruction.owningLob(),
                    instruction.status().name(),
                    instruction.instructionType().name(),
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
        static SecurityEventContext forAction(InstructionAction action, SecurityEventOutcome outcome, String reason) {
            List<String> types = switch (action) {
                case CREATE -> List.of("creation");
                case CANCEL -> List.of("cancellation");
                case VIEW -> List.of("access");
                default -> List.of("change");
            };
            if (outcome == SecurityEventOutcome.FAILURE) {
                types = List.of("access", "denied");
            }
            return new SecurityEventContext("event", List.of("iam"), types, action.name(), outcome.value(), reason);
        }
    }

    public record SecurityEventSource(String application, String service, String version) {
        static SecurityEventSource instructionService() {
            return new SecurityEventSource("instruction-service", "instruction-service", "0.1.0");
        }
    }
}
