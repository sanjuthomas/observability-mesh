package com.observabilitymesh.ofac.model;

import java.time.Instant;

public record OfacScanRequestRef(
        String paymentId,
        int paymentVersion,
        int versionNumber,
        Instant requestedAt
) {
}
