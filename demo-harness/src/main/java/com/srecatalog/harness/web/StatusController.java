package com.srecatalog.harness.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.srecatalog.harness.config.HarnessProperties;
import com.srecatalog.harness.model.SessionCredentials;
import com.srecatalog.harness.service.HarnessAdminAccess;
import com.srecatalog.harness.service.HarnessHelpers;
import com.srecatalog.harness.service.SecurityEventCounter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class StatusController {

    private final HarnessProperties properties;
    private final HarnessAdminAccess adminAccess;
    private final HarnessHelpers helpers;
    private final SecurityEventCounter securityEventCounter;

    public StatusController(
            HarnessProperties properties,
            HarnessAdminAccess adminAccess,
            HarnessHelpers helpers,
            SecurityEventCounter securityEventCounter) {
        this.properties = properties;
        this.adminAccess = adminAccess;
        this.helpers = helpers;
        this.securityEventCounter = securityEventCounter;
    }

    @GetMapping("/api/status")
    public Map<String, Object> status(HttpServletRequest request) {
        adminAccess.requireAdmin(request);
        SessionCredentials adminSession = adminAccess.requireAdminSession(request);

        Map<String, Integer> instructionCounts = new LinkedHashMap<>();
        int totalInstructions = 0;
        try {
            List<JsonNode> instructions = helpers.fetchInstructions(adminSession, null);
            totalInstructions = instructions.size();
            for (JsonNode instruction : instructions) {
                String status = instruction.path("status").asText("UNKNOWN");
                instructionCounts.merge(status, 1, Integer::sum);
            }
        } catch (Exception ignored) {
            // keep partial status payload
        }

        Map<String, Integer> paymentCounts = new LinkedHashMap<>();
        int totalPayments = 0;
        try {
            List<JsonNode> payments = helpers.fetchPayments(adminSession, null);
            totalPayments = payments.size();
            for (JsonNode payment : payments) {
                String status = payment.path("status").asText("UNKNOWN");
                paymentCounts.merge(status, 1, Integer::sum);
            }
        } catch (Exception ignored) {
            // keep partial status payload
        }

        long securityEvents = securityEventCounter.countInstructionEvents();
        long paymentSecurityEvents = securityEventCounter.countPaymentEvents();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("instruction_service_url", properties.instructionServiceUrl());
        payload.put("payment_service_url", properties.paymentServiceUrl());
        payload.put("zitadel_configured", properties.keycloakConfigured());
        payload.put("keycloak_configured", properties.keycloakConfigured());
        payload.put("instruction_total", totalInstructions);
        payload.put("instruction_counts", instructionCounts);
        payload.put("payment_total", totalPayments);
        payload.put("payment_counts", paymentCounts);
        payload.put("security_event_count", securityEvents);
        payload.put("payment_security_event_count", paymentSecurityEvents);
        return payload;
    }
}
