package com.observabilitymesh.payment.repo;

public class ConcurrentModificationException extends IllegalStateException {
    public ConcurrentModificationException(String message) {
        super(message);
    }
}
