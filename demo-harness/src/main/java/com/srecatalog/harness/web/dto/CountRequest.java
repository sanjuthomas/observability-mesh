package com.srecatalog.harness.web.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CountRequest(
        @Min(1) @Max(500) int count
) {
}
