package com.observabilitymesh.payment.model;

import java.time.Instant;

public record VersionedPayment(
        Payment payment,
        int versionNumber,
        Instant validIn,
        Instant validOut
) {
    public boolean isCurrent() {
        return validOut == null;
    }
}
