package com.observabilitymesh.authz.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record UserDirectoryRow(
        @JsonProperty("user_id") String userId,
        @JsonProperty("login_name") String loginName,
        @JsonProperty("given_name") String givenName,
        @JsonProperty("family_name") String familyName,
        @JsonProperty("display_name") String displayName,
        String title,
        String lob,
        List<String> roles,
        List<String> groups,
        @JsonProperty("amount_clubs") List<String> amountClubs,
        @JsonProperty("covering_lobs") List<String> coveringLobs,
        @JsonProperty("supervisor_id") String supervisorId,
        @JsonProperty("supervisor_display_name") String supervisorDisplayName
) {
}
