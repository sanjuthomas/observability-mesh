package com.srecatalog.ofac.repo;

public class ConcurrentModificationException extends RuntimeException {

    public ConcurrentModificationException(String message) {
        super(message);
    }
}
