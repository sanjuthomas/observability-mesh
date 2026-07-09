package com.observabilitymesh.authz.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthzPropertiesTest {

    @Test
    void complianceRoleSetParsesCsv() {
        AuthzProperties properties = new AuthzProperties(
                "http://opa:9181",
                "/tmp/users.yaml",
                "COMPLIANCE_ANALYST, COMPLIANCE_OFFICER");

        assertThat(properties.complianceRoleSet())
                .containsExactlyInAnyOrder("COMPLIANCE_ANALYST", "COMPLIANCE_OFFICER");
    }

    @Test
    void complianceRoleSetReturnsEmptyForBlankValue() {
        AuthzProperties properties = new AuthzProperties("http://opa:9181", "/tmp/users.yaml", "  ");
        assertThat(properties.complianceRoleSet()).isEmpty();
    }
}
