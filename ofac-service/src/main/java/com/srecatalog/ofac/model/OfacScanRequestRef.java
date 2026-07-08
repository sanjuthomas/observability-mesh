package com.srecatalog.ofac.model;

public record OfacScanRequestRef(
        String paymentId,
        int paymentVersion,
        int versionNumber
) {
}
