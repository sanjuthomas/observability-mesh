package com.srecatalog.instruction.model;

public enum SecurityEventOutcome {
    SUCCESS("success"),
    FAILURE("failure");

    private final String value;

    SecurityEventOutcome(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
