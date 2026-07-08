package com.observabilitymesh.instruction.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@ConfigurationProperties(prefix = "observability-mesh.instruction")
public record InstructionProperties(
        String collection,
        String securityEventsDatabase,
        String securityEventsCollection,
        String serviceUserId,
        String serviceUserPassword,
        String complianceRoles,
        String securityEventExcludedUserIds,
        String securityEventViewExcludedUserIds,
        int uiInitialSecurityEventLimit
) {
    public Set<String> complianceRoleSet() {
        return splitCsv(complianceRoles);
    }

    public Set<String> securityEventExcludedUserIdSet() {
        return splitCsv(securityEventExcludedUserIds);
    }

    public Set<String> securityEventViewExcludedUserIdSet() {
        return splitCsv(securityEventViewExcludedUserIds);
    }

    private static Set<String> splitCsv(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
