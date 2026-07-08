package com.observabilitymesh.instruction.service;

import com.observabilitymesh.common.model.PolicyDecision;
import com.observabilitymesh.common.model.Subject;
import com.observabilitymesh.common.model.UserReference;
import com.observabilitymesh.instruction.model.CashSettlementInstruction;
import com.observabilitymesh.instruction.model.InstructionAction;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class InstructionAuthorization {

    private static final Map<String, String> VIOLATION_LABELS = Map.ofEntries(
            Map.entry("MISSING_ROLE_INSTRUCTION_CREATOR", "missing INSTRUCTION_CREATOR role"),
            Map.entry("MISSING_ROLE_INSTRUCTION_APPROVER", "missing INSTRUCTION_APPROVER role"),
            Map.entry("NOT_MIDDLE_OFFICE_GROUP", "not a member of MIDDLE_OFFICE group"),
            Map.entry("SELF_APPROVAL", "creator cannot approve own instruction"),
            Map.entry("ALERT_SUBORDINATE_APPROVING_CREATOR", "approver reports directly to the creator"),
            Map.entry("ALERT_UNAUTHORIZED_SERVICE", "USE requires INSTRUCTION_MARKER service delegation"),
            Map.entry("VIEWER_ACCESS_DENIED", "subject lacks instruction viewer access")
    );

    private InstructionAuthorization() {
    }

    public static Map<String, Object> buildAuthorizationBlock(
            PolicyDecision decision,
            Subject subject,
            InstructionAction action,
            Map<String, Object> resourceContext) {
        String actor = subject.displayName();
        String actionValue = action.name();
        if (decision.allowed()) {
            List<String> basis = decision.allowBasis();
            String summary = basis.isEmpty()
                    ? actor + " was allowed to " + actionValue
                    : actor + " was allowed to " + actionValue + " because " + String.join("; ", basis);
            Map<String, Object> block = baseBlock(subject, actionValue, resourceContext);
            block.put("decision", "allow");
            block.put("allow_basis", basis);
            block.put("violations", List.of());
            block.put("is_alert", false);
            block.put("summary", summary);
            return block;
        }
        List<String> violations = decision.violations();
        String primary = violations.stream().filter(v -> v.startsWith("ALERT_")).findFirst()
                .orElse(violations.isEmpty() ? "POLICY_DENIED" : violations.get(0));
        String primaryLabel = VIOLATION_LABELS.getOrDefault(primary, primary.replace('_', ' ').toLowerCase());
        String summary = actor + " was denied " + actionValue + ": " + primaryLabel;
        if (violations.size() > 1) {
            List<String> extras = violations.stream()
                    .filter(code -> !code.equals(primary))
                    .map(code -> VIOLATION_LABELS.getOrDefault(code, code.replace('_', ' ').toLowerCase()))
                    .toList();
            if (!extras.isEmpty()) {
                summary += " (also: " + String.join("; ", extras) + ")";
            }
        }
        Map<String, Object> block = baseBlock(subject, actionValue, resourceContext);
        block.put("decision", "deny");
        block.put("allow_basis", List.of());
        block.put("violations", violations);
        block.put("is_alert", decision.isAlert());
        block.put("summary", summary);
        return block;
    }

    public static Map<String, Object> instructionResourceContext(CashSettlementInstruction instruction) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("instruction_id", instruction.instructionId());
        context.put("owning_lob", instruction.owningLob());
        context.put("status", instruction.status().name());
        context.put("instruction_type", instruction.instructionType().name());
        context.put("created_by_user_id", instruction.createdBy().userId());
        context.put("created_by_title", instruction.createdBy().title());
        return context;
    }

    public static Map<String, Object> detailsWithAuthorization(Map<String, Object> details, Map<String, Object> authorization) {
        Map<String, Object> merged = details == null ? new LinkedHashMap<>() : new LinkedHashMap<>(details);
        merged.put("authorization", authorization);
        return merged;
    }

    public static UserReference userRef(Subject subject) {
        return new UserReference(
                subject.userId(),
                subject.givenName(),
                subject.familyName(),
                subject.title(),
                subject.lob(),
                subject.roles(),
                subject.supervisorId());
    }

    private static Map<String, Object> baseBlock(Subject subject, String actionValue, Map<String, Object> resourceContext) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("engine", "opa");
        block.put("package", "instruction.lifecycle");
        block.put("action", actionValue);
        block.put("subject_at_decision", subjectAtDecision(subject));
        block.put("resource_context", resourceContext == null ? Map.of() : resourceContext);
        return block;
    }

    private static Map<String, Object> subjectAtDecision(Subject subject) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("user_id", subject.userId());
        map.put("given_name", subject.givenName());
        map.put("family_name", subject.familyName());
        map.put("title", subject.title());
        map.put("roles", subject.roles());
        map.put("groups", subject.groups());
        map.put("lob", subject.lob());
        map.put("supervisor_id", subject.supervisorId());
        map.put("delegated_by", subject.delegatedBy());
        map.put("delegated_by_roles", subject.delegatedByRoles());
        return map;
    }
}
