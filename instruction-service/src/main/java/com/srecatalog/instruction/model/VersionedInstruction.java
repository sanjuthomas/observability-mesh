package com.srecatalog.instruction.model;

import java.time.Instant;

public record VersionedInstruction(
        CashSettlementInstruction instruction,
        int versionNumber,
        Instant validIn,
        Instant validOut
) {
}
