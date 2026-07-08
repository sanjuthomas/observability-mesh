package com.observabilitymesh.sequence.format;

public final class SequenceFormatting {

    private SequenceFormatting() {}

    public static String counterKey(String businessDate, String owningLob, String entityType) {
        return businessDate + "-" + owningLob + "-" + entityType;
    }

    public static String securityEventCounterKey(String resourceId) {
        return resourceId + "-SE";
    }

    public static String sequenceId(String counterKey, long sequenceNumber) {
        return counterKey + "-" + sequenceNumber;
    }

    public static String securityEventSequenceId(String resourceId, long sequenceNumber) {
        return resourceId + "-SE-" + sequenceNumber;
    }
}
