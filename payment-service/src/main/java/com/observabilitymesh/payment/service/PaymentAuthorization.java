package com.observabilitymesh.payment.service;

import com.observabilitymesh.common.model.PolicyDecision;
import com.observabilitymesh.common.model.Subject;
import com.observabilitymesh.common.model.UserReference;
import com.observabilitymesh.common.web.PermissionDeniedException;
import com.observabilitymesh.payment.model.Payment;
import com.observabilitymesh.payment.model.PaymentAction;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PaymentAuthorization {

    private static final Map<String, String> VIOLATION_LABELS = Map.ofEntries(
            Map.entry("ALERT_AMOUNT_EXCEEDS_100B_LIMIT", "payment amount exceeds absolute 100B USD ceiling"),
            Map.entry("ALERT_AMOUNT_EXCEEDS_SUBJECT_LIMIT", "payment amount exceeds subject club limit"),
            Map.entry("NO_LIMIT_GROUP_ASSIGNED", "subject has no payment limit club group"),
            Map.entry("ALERT_UNAPPROVED_INSTRUCTION", "backing instruction is not approved"),
            Map.entry("ALERT_EXPIRED_INSTRUCTION", "backing instruction has expired"),
            Map.entry("ALERT_NOT_MIDDLE_OFFICE_GROUP", "FUNDING_APPROVER is not in MIDDLE_OFFICE group"),
            Map.entry("ALERT_LOB_COVERAGE_VIOLATION", "subject does not cover the instruction LOB"),
            Map.entry("SELF_APPROVAL", "payment creator cannot approve own payment"),
            Map.entry("ALERT_SUBORDINATE_APPROVING_CREATOR", "approver reports directly to payment creator")
    );

    private PaymentAuthorization() {
    }

    public static Map<String, Object> buildAuthorizationBlock(
            PolicyDecision decision,
            Subject subject,
            PaymentAction action,
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

    public static Map<String, Object> paymentResourceContext(
            Payment payment,
            String instructionStatus,
            String instructionEndDate) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("payment_id", payment.paymentId());
        context.put("instruction_id", payment.instructionId());
        context.put("instruction_owning_lob", payment.owningLob());
        context.put("instruction_status", instructionStatus == null || instructionStatus.isBlank() ? "APPROVED" : instructionStatus);
        context.put("instruction_end_date", instructionEndDate == null ? "" : instructionEndDate);
        context.put("payment_amount", payment.amount());
        context.put("payment_currency", payment.currency());
        context.put("payment_status", payment.status().name());
        context.put("created_by_user_id", payment.createdBy().userId());
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

    public static void requireAllowed(PolicyDecision decision, Map<String, Object> authorization) {
        if (!decision.allowed()) {
            throw new PermissionDeniedException(String.valueOf(authorization.get("summary")));
        }
    }

    private static Map<String, Object> baseBlock(Subject subject, String actionValue, Map<String, Object> resourceContext) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("engine", "opa");
        block.put("package", "payment.lifecycle");
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
        map.put("covering_lobs", subject.coveringLobs());
        map.put("lob", subject.lob());
        map.put("supervisor_id", subject.supervisorId());
        return map;
    }
}
