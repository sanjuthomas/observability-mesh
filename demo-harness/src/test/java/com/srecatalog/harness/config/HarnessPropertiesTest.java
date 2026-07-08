package com.srecatalog.harness.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessPropertiesTest {

    @Test
    void appliesDefaults() {
        HarnessProperties properties = new HarnessProperties(
                "http://localhost:9000",
                null,
                "http://localhost:9093",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true);

        assertThat(properties.instructionServiceApiPrefix()).isEqualTo("/api/v1");
        assertThat(properties.paymentServiceApiPrefix()).isEqualTo("/api/v1");
        assertThat(properties.usersFile()).isEqualTo("classpath:users.yaml");
        assertThat(properties.defaultPassword()).isEqualTo("Password1!");
        assertThat(properties.keycloakConfigured()).isTrue();
    }

    @Test
    void treatsNullInstructionUrlAsUnconfigured() {
        HarnessProperties properties = new HarnessProperties(
                null,
                "/api/v1",
                "http://localhost:9093",
                "/api/v1",
                "classpath:users.yaml",
                "Password1!",
                "ssi.local",
                "admin-001",
                "security_events",
                "instruction_service",
                "payment_service",
                false);

        assertThat(properties.keycloakConfigured()).isFalse();
    }

    @Test
    void treatsBlankInstructionUrlAsUnconfigured() {
        HarnessProperties properties = new HarnessProperties(
                "  ",
                "/api/v1",
                "http://localhost:9093",
                "/api/v1",
                "classpath:users.yaml",
                "Password1!",
                "ssi.local",
                "admin-001",
                "security_events",
                "instruction_service",
                "payment_service",
                false);

        assertThat(properties.keycloakConfigured()).isFalse();
    }

    @Test
    void preservesExplicitBlankApiPrefixesAndUsersFile() {
        HarnessProperties properties = new HarnessProperties(
                "http://localhost:9000",
                " ",
                "http://localhost:9093",
                " ",
                " ",
                "secret",
                "example.com",
                "admin-999",
                "events",
                "instruction_events",
                "payment_events",
                false);

        assertThat(properties.instructionServiceApiPrefix()).isEqualTo("/api/v1");
        assertThat(properties.paymentServiceApiPrefix()).isEqualTo("/api/v1");
        assertThat(properties.usersFile()).isEqualTo("classpath:users.yaml");
        assertThat(properties.emailDomain()).isEqualTo("example.com");
        assertThat(properties.adminUserId()).isEqualTo("admin-999");
        assertThat(properties.securityEventsDatabase()).isEqualTo("events");
    }
}
