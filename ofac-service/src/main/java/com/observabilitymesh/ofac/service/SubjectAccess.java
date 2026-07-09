package com.observabilitymesh.ofac.service;

import com.observabilitymesh.common.model.Subject;
import com.observabilitymesh.common.web.PermissionDeniedException;

public final class SubjectAccess {

    private SubjectAccess() {
    }

    public static boolean isPlatformAdmin(Subject subject) {
        return subject.roles().contains("PLATFORM_ADMIN");
    }

    public static void requirePlatformAdmin(Subject subject) {
        if (!isPlatformAdmin(subject)) {
            throw new PermissionDeniedException("PLATFORM_ADMIN role required");
        }
    }
}
