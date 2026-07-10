package com.observabilitymesh.instruction.model;

import java.util.Map;

public record LifecycleEvent(
        String eventId,
        String action,
        String actorUserId,
        String timestamp,
        Map<String, Object> details
) {
}
