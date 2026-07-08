package com.srecatalog.authz.web.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PolicyDecisionResponse(
        boolean allowed,
        List<String> allowBasis,
        List<String> violations,
        boolean isAlert
) {
    public static PolicyDecisionResponse from(com.srecatalog.common.model.PolicyDecision decision) {
        return new PolicyDecisionResponse(
                decision.allowed(),
                decision.allowBasis(),
                decision.violations(),
                decision.isAlert());
    }
}
