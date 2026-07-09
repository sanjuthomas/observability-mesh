package com.observabilitymesh.ofac.repo;

public class OfacScanRequestNotFoundException extends RuntimeException {

    public OfacScanRequestNotFoundException(String paymentId, int paymentVersion) {
        super("OFAC scan request not found for payment " + paymentId + " version " + paymentVersion);
    }
}
