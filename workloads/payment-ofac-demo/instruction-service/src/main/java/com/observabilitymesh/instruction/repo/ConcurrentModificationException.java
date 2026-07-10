package com.observabilitymesh.instruction.repo;

public class ConcurrentModificationException extends RuntimeException {

    public ConcurrentModificationException(String message) {
        super(message);
    }
}
