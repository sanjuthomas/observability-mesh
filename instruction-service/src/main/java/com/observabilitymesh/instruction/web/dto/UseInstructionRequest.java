package com.observabilitymesh.instruction.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UseInstructionRequest(
        @JsonProperty("payment_reference") @NotBlank @Size(max = 128) String paymentReference,
        @JsonProperty("end_to_end_identification") @Size(max = 35) String endToEndIdentification
) {
}
