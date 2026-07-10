package com.observabilitymesh.ofac.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OfacPropertiesTest {

    @Test
    void defaultsMutantModeToOffAndBlankPrefix() {
        OfacProperties properties = new OfacProperties(
                "scan-requests", 30_000, 30_000, 60_000, null, null, 15, 90_000, 120_000);

        assertThat(properties.mutantMode()).isEqualTo(OfacMutantMode.OFF);
        assertThat(properties.mutantPaymentIdPrefix()).isEmpty();
    }
}
