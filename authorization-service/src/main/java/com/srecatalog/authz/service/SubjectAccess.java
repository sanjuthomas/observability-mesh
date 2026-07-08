package com.srecatalog.authz.service;

import com.srecatalog.authz.config.AuthzProperties;
import com.srecatalog.common.model.Subject;

import java.util.Set;

public final class SubjectAccess {

    private SubjectAccess() {
    }

    public static boolean isPlatformAdmin(Subject subject) {
        return subject.roles().contains("PLATFORM_ADMIN");
    }

    public static boolean isComplianceSubject(Subject subject, AuthzProperties properties) {
        if (isPlatformAdmin(subject)) {
            return true;
        }
        Set<String> complianceRoles = properties.complianceRoleSet();
        return subject.roles().stream().anyMatch(complianceRoles::contains);
    }

    public static void requirePlatformAdmin(Subject subject) {
        if (!isPlatformAdmin(subject)) {
            throw new com.srecatalog.common.web.PermissionDeniedException("PLATFORM_ADMIN role required");
        }
    }

    public static void requireCompliance(Subject subject, AuthzProperties properties) {
        if (!isComplianceSubject(subject, properties)) {
            throw new com.srecatalog.common.web.PermissionDeniedException(
                    "COMPLIANCE_ANALYST role required for policy inquiry");
        }
    }
}
