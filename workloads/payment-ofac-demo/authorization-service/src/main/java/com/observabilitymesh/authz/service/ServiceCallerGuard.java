package com.observabilitymesh.authz.service;

import com.observabilitymesh.common.model.Subject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ServiceCallerGuard {

    private final Set<String> authorizedServiceUserIds;

    public ServiceCallerGuard(
            @Value("${observability-mesh.auth.authorized-service-user-ids:}") String authorizedServiceUserIds) {
        this.authorizedServiceUserIds = splitCsv(authorizedServiceUserIds);
    }

    public void requireAuthorizedService(Subject serviceCaller) {
        if (!authorizedServiceUserIds.contains(serviceCaller.userId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "service account " + serviceCaller.userId() + " is not authorized for policy evaluation");
        }
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
