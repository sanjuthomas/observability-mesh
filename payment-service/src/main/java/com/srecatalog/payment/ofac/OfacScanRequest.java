package com.srecatalog.payment.ofac;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record OfacScanRequest(
        String paymentId,
        int paymentVersion,
        int versionNumber,
        String instructionId,
        String owningLob,
        Map<String, Object> debtorAccount,
        Map<String, Object> creditorAccount,
        String creditorName,
        List<Map<String, Object>> intermediaries,
        Instant requestedAt,
        Instant in,
        String out,
        OfacScanLifecycleStatus lifecycleStatus,
        OfacScanResult result
) {
}
