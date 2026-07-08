package com.observabilitymesh.payment.client;

public class InstructionClientException extends RuntimeException {
    public InstructionClientException(String message) {
        super(message);
    }

    public InstructionClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
