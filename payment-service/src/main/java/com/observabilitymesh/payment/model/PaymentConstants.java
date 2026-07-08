package com.observabilitymesh.payment.model;

public final class PaymentConstants {

    public static final String CURRENT_OUT = "9999-12-31T23:59:59Z";

    private PaymentConstants() {
    }

    public static String documentKey(String paymentId, int versionNumber) {
        return paymentId + "|" + versionNumber;
    }

    public static String paymentIdFromDocumentKey(String documentKey) {
        int idx = documentKey.lastIndexOf('|');
        return idx < 0 ? documentKey : documentKey.substring(0, idx);
    }
}
