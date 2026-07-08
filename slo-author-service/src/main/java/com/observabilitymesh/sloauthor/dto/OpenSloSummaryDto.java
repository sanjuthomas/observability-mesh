package com.observabilitymesh.sloauthor.dto;

import java.time.Instant;

public record OpenSloSummaryDto(
    String id,
    String logicalKey,
    String apiVersion,
    String kind,
    String name,
    String displayName,
    int version,
    boolean stale,
    Instant createdAt,
    String createdBy
) {}
