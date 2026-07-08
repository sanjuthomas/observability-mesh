package com.srecatalog.harness.seed;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record SeedUser(
        @JsonProperty("user_id") String userId,
        @JsonProperty("given_name") String givenName,
        @JsonProperty("family_name") String familyName,
        String title,
        List<String> roles,
        List<String> groups,
        String lob,
        @JsonProperty("supervisor_id") String supervisorId,
        @JsonProperty("covering_lobs") List<String> coveringLobs
) {
    public SeedUser {
        roles = roles == null ? List.of() : List.copyOf(roles);
        groups = groups == null ? List.of() : List.copyOf(groups);
        coveringLobs = coveringLobs == null ? List.of() : List.copyOf(coveringLobs);
    }
}

record SeedFileRoot(
        Map<String, String> defaults,
        List<SeedUser> users
) {
}
