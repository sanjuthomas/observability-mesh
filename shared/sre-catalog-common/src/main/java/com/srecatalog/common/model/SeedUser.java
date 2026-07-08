package com.srecatalog.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record SeedUser(
        @JsonProperty("user_id") String userId,
        @JsonProperty("given_name") String givenName,
        @JsonProperty("family_name") String familyName,
        String title,
        List<String> roles,
        String lob,
        List<String> groups,
        @JsonProperty("supervisor_id") String supervisorId,
        @JsonProperty("covering_lobs") List<String> coveringLobs
) {
    public SeedUser {
        roles = roles == null ? List.of() : List.copyOf(roles);
        groups = groups == null ? List.of() : List.copyOf(groups);
        coveringLobs = coveringLobs == null ? List.of() : List.copyOf(coveringLobs);
    }

    public Subject toSubject() {
        return new Subject(userId, givenName, familyName, title, lob, roles, groups, supervisorId, coveringLobs, null, List.of());
    }
}
