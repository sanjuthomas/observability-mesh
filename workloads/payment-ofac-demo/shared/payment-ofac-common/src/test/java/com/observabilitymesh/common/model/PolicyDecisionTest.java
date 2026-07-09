package com.observabilitymesh.common.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyDecisionTest {

    @Test
    void allowFactoryCopiesBasis() {
        PolicyDecision decision = PolicyDecision.allow(List.of("role gate"));
        assertThat(decision.allowed()).isTrue();
        assertThat(decision.allowBasis()).containsExactly("role gate");
        assertThat(decision.violations()).isEmpty();
        assertThat(decision.isAlert()).isFalse();
    }

    @Test
    void denyFactoryCapturesViolations() {
        PolicyDecision decision = PolicyDecision.deny(List.of("self approval"), true);
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.violations()).containsExactly("self approval");
        assertThat(decision.isAlert()).isTrue();
    }

    @Test
    void constructorNormalizesNullLists() {
        PolicyDecision decision = new PolicyDecision(true, null, null, false);
        assertThat(decision.allowBasis()).isEmpty();
        assertThat(decision.violations()).isEmpty();
    }
}
