package com.srecatalog.payment.client;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class InstructionStateException extends ResponseStatusException {
    public InstructionStateException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
