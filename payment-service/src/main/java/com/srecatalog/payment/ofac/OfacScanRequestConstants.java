package com.srecatalog.payment.ofac;

public final class OfacScanRequestConstants {

    public static final String CURRENT_OUT = "9999-12-31T23:59:59Z";

    private OfacScanRequestConstants() {
    }

    public static String documentKey(String paymentId, int paymentVersion, int versionNumber) {
        return paymentId + "|" + paymentVersion + "|" + versionNumber;
    }
}
