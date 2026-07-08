package com.observabilitymesh.payment.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentPropertiesTest {

    @Test
    void splitCsvHandlesNullAndBlank() {
        PaymentProperties properties = new PaymentProperties(
                "payments", "ofac-scan-requests", "security_events", "payment_service",
                "svc-payment", "Password1!", null, null, "  ", 200);
        assertThat(properties.complianceRoleSet()).isEmpty();
        assertThat(properties.securityEventExcludedUserIdSet()).isEmpty();
        assertThat(properties.securityEventViewExcludedUserIdSet()).isEmpty();
    }

    @Test
    void splitCsvTrimsValues() {
        PaymentProperties properties = new PaymentProperties(
                "payments", "ofac-scan-requests", "security_events", "payment_service",
                "svc-payment", "Password1!", " ROLE_A , ROLE_B ", " admin-1 , ", "", 200);
        assertThat(properties.complianceRoleSet()).containsExactlyInAnyOrder("ROLE_A", "ROLE_B");
        assertThat(properties.securityEventExcludedUserIdSet()).containsExactly("admin-1");
    }
}
