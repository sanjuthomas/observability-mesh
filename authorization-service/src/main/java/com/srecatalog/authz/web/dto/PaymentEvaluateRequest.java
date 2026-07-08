package com.srecatalog.authz.web.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PaymentEvaluateRequest(
        @NotBlank String action,
        @NotNull Map<String, Object> payment,
        String instructionEndDate,
        String instructionStatus,
        SubjectPayload subject
) {
}
