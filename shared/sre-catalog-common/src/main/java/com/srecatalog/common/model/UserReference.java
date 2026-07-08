package com.srecatalog.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UserReference(
        @JsonProperty("user_id") String userId,
        @JsonProperty("given_name") String givenName,
        @JsonProperty("family_name") String familyName,
        String title,
        String lob,
        java.util.List<String> roles,
        @JsonProperty("supervisor_id") String supervisorId
) {
}
