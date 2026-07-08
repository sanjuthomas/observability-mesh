package com.observabilitymesh.payment.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CreatePaymentRequest(
        @NotBlank @JsonProperty("instruction_id") String instructionId,
        @NotBlank @JsonProperty("value_date") String valueDate,
        @Positive double amount
) {
}
