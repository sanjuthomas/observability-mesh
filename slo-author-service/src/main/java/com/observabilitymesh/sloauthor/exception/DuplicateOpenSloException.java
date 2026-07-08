package com.observabilitymesh.sloauthor.exception;

public class DuplicateOpenSloException extends RuntimeException {

    private final String logicalKey;

    public DuplicateOpenSloException(String logicalKey) {
        super("An active OpenSLO document already exists with key: " + logicalKey);
        this.logicalKey = logicalKey;
    }

    public String getLogicalKey() {
        return logicalKey;
    }
}
