package com.observabilitymesh.instruction.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReleaseUseInstructionRequest(
        @JsonProperty("payment_reference") @NotBlank @Size(max = 128) String paymentReference
) {
}
