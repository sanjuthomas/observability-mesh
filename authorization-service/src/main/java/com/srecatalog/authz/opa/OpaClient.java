package com.srecatalog.authz.opa;

import com.srecatalog.common.model.PolicyDecision;
import com.srecatalog.common.model.Subject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpaClient {

    private static final String INSTRUCTION_PACKAGE = "instruction/lifecycle";
    private static final String PAYMENT_PACKAGE = "payment/lifecycle";
    private static final String INSTRUCTION_ACTION = "APPROVE";
    private static final String PAYMENT_ACTION = "APPROVE";

    private final RestClient restClient;
    private final String baseUrl;

    public OpaClient(RestClient.Builder builder, @Value("${sre-catalog.authz.opa-url}") String opaUrl) {
        this.baseUrl = opaUrl.replaceAll("/$", "");
        this.restClient = builder.baseUrl(this.baseUrl).build();
    }

    public Map<String, Object> policyHealth(int minimumPolicies) {
        try {
            List<String> policyIds = listPolicyIds();
            int count = policyIds.size();
            if (count < minimumPolicies) {
                return Map.of(
                        "ok", false,
                        "policy_count", count,
                        "detail", "expected at least " + minimumPolicies + " policies");
            }

            Map<String, Object> smokeInput = Map.of(
                    "input", Map.of(
                            "action", "CREATE",
                            "subject", Map.of(
                                    "user_id", "mo-100",
                                    "title", "Analyst",
                                    "roles", List.of("INSTRUCTION_CREATOR"),
                                    "groups", List.of("MIDDLE_OFFICE")),
                            "instruction", Map.of(
                                    "status", "DRAFT",
                                    "type", "SINGLE_USE",
                                    "owning_lob", "FICC",
                                    "effective_date", "2026-07-04T00:00:00Z",
                                    "end_date", "2027-07-04T00:00:00Z",
                                    "created_by", Map.of(
                                            "user_id", "mo-100",
                                            "title", "Analyst")),
                            "account", Map.of("owning_lob", "FICC")));

            boolean allowed = Boolean.TRUE.equals(postData("instruction/lifecycle/allow", smokeInput));
            if (!allowed) {
                return Map.of(
                        "ok", false,
                        "policy_count", count,
                        "detail", "instruction CREATE smoke evaluation denied");
            }
            return Map.of("ok", true, "policy_count", count, "detail", "policies loaded");
        } catch (Exception ex) {
            return Map.of("ok", false, "policy_count", 0, "detail", ex.getMessage());
        }
    }

    public List<String> listPolicyIds() {
        OpaPoliciesResponse body = restClient.get()
                .uri("/v1/policies")
                .retrieve()
                .body(OpaPoliciesResponse.class);
        if (body == null || body.result() == null) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (Map<String, Object> item : body.result()) {
            Object id = item.get("id");
            if (id != null) {
                ids.add(String.valueOf(id));
            }
        }
        return ids;
    }

    public PolicyDecision evaluateInstruction(
            String action,
            Subject subject,
            Map<String, Object> instruction,
            Map<String, Object> account) {
        Map<String, Object> payload = Map.of(
                "input", Map.of(
                        "action", action,
                        "subject", subject.toOpaSubject(),
                        "instruction", instruction,
                        "account", account));
        return evaluate(INSTRUCTION_PACKAGE, payload);
    }

    public PolicyDecision evaluatePayment(
            String action,
            Subject subject,
            Map<String, Object> payment,
            String instructionEndDate,
            String instructionStatus) {
        Map<String, Object> paymentInput = new LinkedHashMap<>(payment);
        if (instructionEndDate != null && !instructionEndDate.isBlank()) {
            paymentInput.put("instruction_end_date", instructionEndDate);
        }
        if (instructionStatus != null && !instructionStatus.isBlank()) {
            paymentInput.put("instruction_status", instructionStatus);
        }
        Map<String, Object> payload = Map.of(
                "input", Map.of(
                        "action", action,
                        "subject", subject.toOpaSubject(),
                        "payment", paymentInput));
        return evaluate(PAYMENT_PACKAGE, payload);
    }

    public ApprovalResult canApproveInstruction(
            Subject subject,
            Map<String, Object> opaInstruction,
            Map<String, Object> opaAccount) {
        Map<String, Object> payload = Map.of(
                "input", Map.of(
                        "action", INSTRUCTION_ACTION,
                        "subject", subject.toOpaSubject(),
                        "instruction", opaInstruction,
                        "account", opaAccount));
        return canApprove(INSTRUCTION_PACKAGE, payload);
    }

    public ApprovalResult canApprovePayment(
            Subject subject,
            Map<String, Object> opaPayment) {
        Map<String, Object> payload = Map.of(
                "input", Map.of(
                        "action", PAYMENT_ACTION,
                        "subject", subject.toOpaSubject(),
                        "payment", opaPayment));
        return canApprove(PAYMENT_PACKAGE, payload);
    }

    private PolicyDecision evaluate(String packageName, Map<String, Object> payload) {
        boolean allowed = Boolean.TRUE.equals(postData(packageName + "/allow", payload));
        if (allowed) {
            List<String> basis = asStringList(postData(packageName + "/allow_basis", payload));
            return PolicyDecision.allow(basis);
        }
        List<String> violations = violationCodes(postData(packageName + "/violations", payload));
        boolean isAlert = Boolean.TRUE.equals(postData(packageName + "/is_alert", payload));
        return PolicyDecision.deny(violations, isAlert);
    }

    private ApprovalResult canApprove(String packageName, Map<String, Object> payload) {
        boolean allowed = Boolean.TRUE.equals(postData(packageName + "/allow", payload));
        if (!allowed) {
            return new ApprovalResult(false, List.of());
        }
        return new ApprovalResult(true, asStringList(postData(packageName + "/allow_basis", payload)));
    }

    private Object postData(String path, Map<String, Object> payload) {
        OpaDataResponse body = restClient.post()
                .uri("/v1/data/{path}", path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(OpaDataResponse.class);
        return body == null ? null : body.result();
    }

    private static List<String> asStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }

    @SuppressWarnings("unchecked")
    private static List<String> violationCodes(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return List.of();
        }
        List<String> codes = new ArrayList<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (Boolean.TRUE.equals(entry.getValue())) {
                codes.add(String.valueOf(entry.getKey()));
            }
        }
        codes.sort(String::compareTo);
        return codes;
    }

    public record ApprovalResult(boolean allowed, List<String> allowBasis) {
    }

    private record OpaDataResponse(Object result) {
    }

    private record OpaPoliciesResponse(List<Map<String, Object>> result) {
    }
}
