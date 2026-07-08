package com.observabilitymesh.sloprovisioner.model;

import java.time.Instant;
import java.util.Map;

public record OpenSloDocumentView(
        String id,
        String logicalKey,
        int version,
        boolean stale,
        String kind,
        String name,
        Map<String, Object> content
) {
}
