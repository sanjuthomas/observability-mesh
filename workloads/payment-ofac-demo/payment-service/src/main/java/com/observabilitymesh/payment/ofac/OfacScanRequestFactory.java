package com.observabilitymesh.payment.ofac;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.observabilitymesh.payment.model.Payment;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OfacScanRequestFactory {

    private OfacScanRequestFactory() {
    }

    public static OfacScanRequest from(
            Payment payment,
            JsonNode instruction,
            int paymentVersion,
            ObjectMapper objectMapper) {
        Instant now = Instant.now();
        return new OfacScanRequest(
                payment.paymentId(),
                paymentVersion,
                1,
                payment.instructionId(),
                payment.owningLob(),
                toMap(objectMapper, instruction.get("debtor_account")),
                toMap(objectMapper, instruction.get("creditor_account")),
                creditorName(instruction),
                intermediaryAgents(objectMapper, instruction),
                now,
                now,
                OfacScanRequestConstants.CURRENT_OUT,
                OfacScanLifecycleStatus.OPEN,
                null);
    }

    private static String creditorName(JsonNode instruction) {
        JsonNode creditor = instruction.get("creditor");
        if (creditor == null || creditor.isNull()) {
            return "";
        }
        JsonNode name = creditor.get("name");
        return name == null || name.isNull() ? "" : name.asText();
    }

    private static List<Map<String, Object>> intermediaryAgents(ObjectMapper objectMapper, JsonNode instruction) {
        JsonNode intermediaries = instruction.get("intermediary_agents");
        if (intermediaries == null || !intermediaries.isArray()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonNode entry : intermediaries) {
            Map<String, Object> mapped = toMap(objectMapper, entry);
            if (!mapped.isEmpty()) {
                result.add(mapped);
            }
        }
        return List.copyOf(result);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(ObjectMapper objectMapper, JsonNode node) {
        if (node == null || node.isNull()) {
            return Map.of();
        }
        return new LinkedHashMap<>(objectMapper.convertValue(node, Map.class));
    }
}
