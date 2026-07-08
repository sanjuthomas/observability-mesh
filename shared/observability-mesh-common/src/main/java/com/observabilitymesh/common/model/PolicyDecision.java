package com.observabilitymesh.common.model;

import java.util.List;

public record PolicyDecision(
        boolean allowed,
        List<String> allowBasis,
        List<String> violations,
        boolean isAlert
) {
    public PolicyDecision {
        allowBasis = allowBasis == null ? List.of() : List.copyOf(allowBasis);
        violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public static PolicyDecision allow(List<String> allowBasis) {
        return new PolicyDecision(true, allowBasis, List.of(), false);
    }

    public static PolicyDecision deny(List<String> violations, boolean isAlert) {
        return new PolicyDecision(false, List.of(), violations, isAlert);
    }
}
