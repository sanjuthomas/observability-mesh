package com.srecatalog.payment.web.dto;

import jakarta.validation.constraints.Size;

public record CancelPaymentRequest(
        @Size(max = 1024) String reason
) {
}
