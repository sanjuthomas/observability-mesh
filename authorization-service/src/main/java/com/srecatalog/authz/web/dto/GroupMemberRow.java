package com.srecatalog.authz.web.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GroupMemberRow(
        String userId,
        String displayName,
        String title,
        List<String> roles,
        List<String> groups,
        List<String> coveringLobs
) {
}
