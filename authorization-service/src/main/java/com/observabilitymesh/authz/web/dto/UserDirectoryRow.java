package com.observabilitymesh.authz.web.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record UserDirectoryRow(
        String userId,
        String loginName,
        String givenName,
        String familyName,
        String displayName,
        String title,
        String lob,
        List<String> roles,
        List<String> groups,
        List<String> amountClubs,
        List<String> coveringLobs,
        String supervisorId,
        String supervisorDisplayName
) {
}
