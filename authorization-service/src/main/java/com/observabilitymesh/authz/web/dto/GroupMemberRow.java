package com.observabilitymesh.authz.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record GroupMemberRow(
        @JsonProperty("user_id") String userId,
        @JsonProperty("display_name") String displayName,
        String title,
        List<String> roles,
        List<String> groups,
        @JsonProperty("covering_lobs") List<String> coveringLobs
) {
}
