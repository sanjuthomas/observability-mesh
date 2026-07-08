package com.srecatalog.harness.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sre-catalog.harness")
public record HarnessProperties(
        String instructionServiceUrl,
        String instructionServiceApiPrefix,
        String paymentServiceUrl,
        String paymentServiceApiPrefix,
        String usersFile,
        String defaultPassword,
        String emailDomain,
        String adminUserId,
        String securityEventsDatabase,
        String securityEventsCollection,
        String paymentSecurityEventsCollection,
        boolean verifySecurityEvents
) {
    public HarnessProperties {
        if (instructionServiceApiPrefix == null || instructionServiceApiPrefix.isBlank()) {
            instructionServiceApiPrefix = "/api/v1";
        }
        if (paymentServiceApiPrefix == null || paymentServiceApiPrefix.isBlank()) {
            paymentServiceApiPrefix = "/api/v1";
        }
        if (usersFile == null || usersFile.isBlank()) {
            usersFile = "classpath:users.yaml";
        }
        if (defaultPassword == null) {
            defaultPassword = "Password1!";
        }
        if (emailDomain == null) {
            emailDomain = "ssi.local";
        }
        if (adminUserId == null) {
            adminUserId = "admin-001";
        }
        if (securityEventsDatabase == null) {
            securityEventsDatabase = "security_events";
        }
        if (securityEventsCollection == null) {
            securityEventsCollection = "instruction_service";
        }
        if (paymentSecurityEventsCollection == null) {
            paymentSecurityEventsCollection = "payment_service";
        }
    }

    public boolean keycloakConfigured() {
        return instructionServiceUrl != null && !instructionServiceUrl.isBlank();
    }
}
