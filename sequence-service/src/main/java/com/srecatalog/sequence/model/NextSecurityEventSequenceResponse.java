package com.srecatalog.sequence.model;

public record NextSecurityEventSequenceResponse(
        String sequenceId,
        String resourceId,
        long sequenceNumber,
        String counterKey
) {}
