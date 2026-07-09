package com.observabilitymesh.authzclient;

import com.observabilitymesh.common.model.PolicyDecision;
import com.observabilitymesh.common.model.Subject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AuthzClient {

    private final RestClient restClient;
    private final String baseUrl;

    public AuthzClient(RestClient.Builder builder, @Value("${observability-mesh.authz.url}") String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.restClient = builder.baseUrl(this.baseUrl).build();
    }

    public PolicyDecision evaluateInstruction(
            String action,
            Map<String, Object> instruction,
            Map<String, Object> account,
            String serviceToken,
            String serviceSessionId,
            String userToken,
            String userSessionId,
            Subject subject) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", action);
        payload.put("instruction", instruction);
        payload.put("account", account);
        if (userToken != null && serviceToken != null) {
            return post("/api/v1/authorization/instructions/evaluate", payload,
                    oboHeaders(serviceToken, serviceSessionId, userToken, userSessionId));
        }
        payload.put("subject", subjectToMap(subject));
        return post("/api/v1/authorization/instructions/evaluate", payload,
                serviceHeaders(serviceToken, serviceSessionId));
    }

    public PolicyDecision evaluatePayment(
            String action,
            Map<String, Object> payment,
            String instructionEndDate,
            String instructionStatus,
            String serviceToken,
            String serviceSessionId,
            String userToken,
            String userSessionId,
            Subject subject) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", action);
        payload.put("payment", payment);
        payload.put("instruction_end_date", instructionEndDate);
        payload.put("instruction_status", instructionStatus);
        if (userToken != null && serviceToken != null) {
            return post("/api/v1/authorization/payments/evaluate", payload,
                    oboHeaders(serviceToken, serviceSessionId, userToken, userSessionId));
        }
        payload.put("subject", subjectToMap(subject));
        return post("/api/v1/authorization/payments/evaluate", payload,
                serviceHeaders(serviceToken, serviceSessionId));
    }

    public Map<String, Object> eligibleInstructionApprovers(
            Map<String, Object> instruction,
            String serviceToken,
            String serviceSessionId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("instruction", instruction);
        InstructionEligibleApproversResponse body = restClient.post()
                .uri("/api/v1/authorization/instructions/eligible-approvers")
                .headers(h -> h.addAll(serviceHeaders(serviceToken, serviceSessionId)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(InstructionEligibleApproversResponse.class);
        if (body == null) {
            throw new AuthzClientException("Empty response from authorization-service");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("instruction_id", body.instructionId());
        result.put("instruction_status", body.instructionStatus());
        result.put("instruction_type", body.instructionType());
        result.put("owning_lob", body.owningLob());
        result.put("created_by_user_id", body.createdByUserId());
        result.put("created_by_title", body.createdByTitle());
        result.put("evaluated_at", body.evaluatedAt());
        result.put("eligible", body.eligible());
        result.put("prospective_eligible", body.prospectiveEligible());
        result.put("candidates_evaluated", body.candidatesEvaluated());
        result.put("approval_blocked_reason", body.approvalBlockedReason());
        return result;
    }

    public Map<String, Object> eligiblePaymentApprovers(
            Map<String, Object> payment,
            String instructionStatus,
            String instructionEndDate,
            String serviceToken,
            String serviceSessionId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("payment", payment);
        payload.put("instruction_status", instructionStatus);
        payload.put("instruction_end_date", instructionEndDate);
        EligibleApproversResponse body = restClient.post()
                .uri("/api/v1/authorization/payments/eligible-approvers")
                .headers(h -> h.addAll(serviceHeaders(serviceToken, serviceSessionId)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(EligibleApproversResponse.class);
        if (body == null) {
            throw new AuthzClientException("Empty response from authorization-service");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("payment_id", body.paymentId());
        result.put("instruction_id", body.instructionId());
        result.put("payment_status", body.paymentStatus());
        result.put("amount", body.amount());
        result.put("currency", body.currency());
        result.put("owning_lob", body.owningLob());
        result.put("instruction_status", body.instructionStatus());
        result.put("evaluated_at", body.evaluatedAt());
        result.put("eligible", body.eligible());
        result.put("prospective_eligible", body.prospectiveEligible());
        result.put("candidates_evaluated", body.candidatesEvaluated());
        result.put("approval_blocked_reason", body.approvalBlockedReason());
        return result;
    }

    private PolicyDecision post(String path, Map<String, Object> payload, HttpHeaders headers) {
        PolicyDecisionResponse body = restClient.post()
                .uri(path)
                .headers(h -> h.addAll(headers))
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(PolicyDecisionResponse.class);
        if (body == null) {
            throw new AuthzClientException("Empty response from authorization-service");
        }
        return new PolicyDecision(body.allowed(), body.allowBasis(), body.violations(), body.isAlert());
    }

    private static HttpHeaders oboHeaders(String serviceToken, String serviceSessionId, String userToken, String userSessionId) {
        HttpHeaders headers = serviceHeaders(serviceToken, serviceSessionId);
        headers.set("X-On-Behalf-Of", userToken);
        if (userSessionId != null && !userSessionId.isBlank()) {
            headers.set("X-On-Behalf-Of-Session-Id", userSessionId);
        }
        return headers;
    }

    private static HttpHeaders serviceHeaders(String serviceToken, String serviceSessionId) {
        HttpHeaders headers = new HttpHeaders();
        if (serviceToken != null) {
            headers.setBearerAuth(serviceToken.startsWith("Bearer ") ? serviceToken.substring(7) : serviceToken);
        }
        if (serviceSessionId != null && !serviceSessionId.isBlank()) {
            headers.set("X-Session-Id", serviceSessionId);
        }
        return headers;
    }

    private static Map<String, Object> subjectToMap(Subject subject) {
        Map<String, Object> map = new LinkedHashMap<>(subject.toOpaSubject());
        map.put("given_name", subject.givenName());
        map.put("family_name", subject.familyName());
        return map;
    }

    private record PolicyDecisionResponse(
            boolean allowed,
            List<String> allowBasis,
            List<String> violations,
            boolean isAlert
    ) {
    }

    private record InstructionEligibleApproversResponse(
            String instructionId,
            String instructionStatus,
            String instructionType,
            String owningLob,
            String createdByUserId,
            String createdByTitle,
            String evaluatedAt,
            List<Map<String, Object>> eligible,
            List<Map<String, Object>> prospectiveEligible,
            int candidatesEvaluated,
            String approvalBlockedReason
    ) {
    }

    private record EligibleApproversResponse(
            String paymentId,
            String instructionId,
            String paymentStatus,
            double amount,
            String currency,
            String owningLob,
            String instructionStatus,
            String evaluatedAt,
            List<Map<String, Object>> eligible,
            List<Map<String, Object>> prospectiveEligible,
            int candidatesEvaluated,
            String approvalBlockedReason
    ) {
    }
}
