package com.srecatalog.authz.web.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PaymentEligibilityContext(
        @NotBlank String paymentId,
        @NotBlank String instructionId,
        int instructionVersion,
        @NotBlank String status,
        double amount,
        @NotBlank String currency,
        @NotBlank String owningLob,
        String instructionType,
        @NotBlank String createdByUserId,
        String createdBySupervisorId
) {
}
