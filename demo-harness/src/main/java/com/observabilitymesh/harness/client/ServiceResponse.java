package com.observabilitymesh.harness.client;

import com.fasterxml.jackson.databind.JsonNode;

public record ServiceResponse(int statusCode, String body, JsonNode json) {

    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }

    public String text(int maxLength) {
        String trimmed = body == null ? "" : body.strip();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength);
    }
}
