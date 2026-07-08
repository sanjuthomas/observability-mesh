package com.srecatalog.sloprovisioner.model;

import java.time.Instant;

public record SloProvisionState(
        String logicalKey,
        int opensloVersion,
        ProvisionStatus status,
        String rulesFileName,
        String contentHash,
        Instant lastSyncedAt,
        String lastError
) {
}
