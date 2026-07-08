package com.srecatalog.authz.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@ConfigurationProperties(prefix = "sre-catalog.authz")
public record AuthzProperties(
        String opaUrl,
        String usersFile,
        String complianceRoles
) {
    public Set<String> complianceRoleSet() {
        return splitCsv(complianceRoles);
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
