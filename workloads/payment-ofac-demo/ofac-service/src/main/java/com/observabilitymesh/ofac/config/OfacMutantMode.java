package com.observabilitymesh.ofac.config;

/**
 * Demo mutants that skew OFAC scan timing and results to breach seeded OpenSLO SLOs.
 */
public enum OfacMutantMode {
    OFF,
    LATENCY,
    COMPLETION,
    BOTH;

    public boolean affectsLatency() {
        return this == LATENCY || this == BOTH;
    }

    public boolean affectsCompletion() {
        return this == COMPLETION || this == BOTH;
    }
}
