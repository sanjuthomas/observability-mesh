package com.observabilitymesh.sloprovisioner.service;

public class ProvisionDocumentNotFoundException extends RuntimeException {

    public ProvisionDocumentNotFoundException(String kind, String name) {
        super(kind + " not found: " + name);
    }
}
