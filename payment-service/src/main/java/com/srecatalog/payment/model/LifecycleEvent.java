package com.srecatalog.payment.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public record LifecycleEvent(
        @JsonProperty("event_id") String eventId,
        String action,
        @JsonProperty("actor_user_id") String actorUserId,
        String timestamp,
        Map<String, Object> details
) {
    public LifecycleEvent {
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
