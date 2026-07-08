package com.observabilitymesh.authz.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record UserDirectoryResponse(
        int count,
        @JsonProperty("email_domain") String emailDomain,
        List<UserDirectoryRow> users
) {
}
