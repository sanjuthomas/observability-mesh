package com.srecatalog.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record Subject(
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
    public Subject {
        groups = groups == null ? List.of() : List.copyOf(groups);
        coveringLobs = coveringLobs == null ? List.of() : List.copyOf(coveringLobs);
        delegatedByRoles = delegatedByRoles == null ? List.of() : List.copyOf(delegatedByRoles);
        roles = List.copyOf(roles);
    }

    @JsonProperty("user_id")
    public String userIdJson() {
        return userId;
    }

    public String displayName() {
        if (familyName != null && !familyName.isBlank() && givenName != null && !givenName.isBlank()) {
            return familyName + ", " + givenName + " (" + userId + ")";
        }
        return userId;
    }

    public Map<String, Object> toOpaSubject() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("user_id", userId);
        payload.put("title", title);
        payload.put("roles", roles);
        payload.put("groups", groups);
        payload.put("covering_lobs", coveringLobs);
        payload.put("delegated_by_roles", delegatedByRoles);
        if (lob != null) {
            payload.put("lob", lob);
        }
        if (supervisorId != null) {
            payload.put("supervisor_id", supervisorId);
        }
        return payload;
    }
}
