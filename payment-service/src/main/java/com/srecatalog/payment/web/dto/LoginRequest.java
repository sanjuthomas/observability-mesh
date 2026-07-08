package com.srecatalog.payment.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank @JsonProperty("user_id") String userId,
        @NotBlank String password
) {
}
