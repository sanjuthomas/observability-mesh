package com.srecatalog.sequence.model;

public record NextSequenceResponse(
        String sequenceId,
        String businessDate,
        String owningLob,
        String entityType,
        long sequenceNumber,
        String counterKey
) {}
