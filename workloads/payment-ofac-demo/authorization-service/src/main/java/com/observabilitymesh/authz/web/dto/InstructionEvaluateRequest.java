package com.observabilitymesh.authz.web.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record InstructionEvaluateRequest(
        @NotBlank String action,
        @NotNull Map<String, Object> instruction,
        @NotNull Map<String, Object> account,
        SubjectPayload subject
) {
}
