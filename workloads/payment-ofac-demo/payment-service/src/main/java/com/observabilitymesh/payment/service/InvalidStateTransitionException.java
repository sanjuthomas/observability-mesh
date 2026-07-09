package com.observabilitymesh.payment.service;

public class InvalidStateTransitionException extends IllegalStateException {
    public InvalidStateTransitionException(String message) {
        super(message);
    }
}
