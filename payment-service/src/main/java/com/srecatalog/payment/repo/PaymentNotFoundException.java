package com.srecatalog.payment.repo;

public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(String paymentId) {
        super("payment " + paymentId + " not found");
    }
}
