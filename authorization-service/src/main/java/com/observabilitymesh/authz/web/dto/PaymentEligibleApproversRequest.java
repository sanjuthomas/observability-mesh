package com.observabilitymesh.authz.web.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PaymentEligibleApproversRequest(
        @NotNull PaymentEligibilityContext payment,
        @NotBlank String instructionStatus,
        String instructionEndDate
) {
}
