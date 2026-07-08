package com.observabilitymesh.payment.client;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class InstructionNotFoundException extends ResponseStatusException {
    public InstructionNotFoundException(String instructionId) {
        super(HttpStatus.NOT_FOUND, "instruction " + instructionId + " not found");
    }
}
