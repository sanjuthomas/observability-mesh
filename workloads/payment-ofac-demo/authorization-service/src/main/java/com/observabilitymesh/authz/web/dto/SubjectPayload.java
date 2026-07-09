package com.observabilitymesh.authz.web.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.observabilitymesh.common.model.Subject;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SubjectPayload(
        @NotBlank String userId,
        String givenName,
        String familyName,
        @NotBlank String title,
        String lob,
        @NotEmpty List<String> roles,
        List<String> groups,
        String supervisorId,
        List<String> coveringLobs,
        String delegatedBy,
        List<String> delegatedByRoles
) {
    public Subject toSubject() {
        return new Subject(
                userId,
                givenName,
                familyName,
                title,
                lob,
                roles,
                groups,
                supervisorId,
                coveringLobs,
                delegatedBy,
                delegatedByRoles);
    }
}
