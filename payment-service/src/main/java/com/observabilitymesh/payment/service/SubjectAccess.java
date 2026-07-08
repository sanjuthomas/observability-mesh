package com.observabilitymesh.payment.service;

import com.observabilitymesh.common.model.Subject;
import com.observabilitymesh.payment.config.PaymentProperties;
import com.observabilitymesh.payment.model.Payment;

import java.util.Set;

public final class SubjectAccess {

    private SubjectAccess() {
    }

    public static boolean isPlatformAdmin(Subject subject) {
        return subject.roles().contains("PLATFORM_ADMIN");
    }

    public static boolean isComplianceSubject(Subject subject, PaymentProperties properties) {
        if (isPlatformAdmin(subject)) {
            return true;
        }
        Set<String> complianceRoles = properties.complianceRoleSet();
        return subject.roles().stream().anyMatch(complianceRoles::contains);
    }

    public static void requirePlatformAdmin(Subject subject) {
        if (!isPlatformAdmin(subject)) {
            throw new com.observabilitymesh.common.web.PermissionDeniedException("PLATFORM_ADMIN role required");
        }
    }

    public static void requireCompliance(Subject subject, PaymentProperties properties) {
        if (!isComplianceSubject(subject, properties)) {
            throw new com.observabilitymesh.common.web.PermissionDeniedException(
                    "COMPLIANCE_ANALYST role required for policy inquiry");
        }
    }

    public static boolean canViewPayment(Subject subject, Payment payment) {
        if (isPlatformAdmin(subject)) {
            return true;
        }
        if (subject.userId().equals(payment.createdBy().userId())) {
            return true;
        }
        String lob = payment.owningLob();
        Set<String> roles = Set.copyOf(subject.roles());
        if (roles.contains("PAYMENT_CREATOR") && (coversLob(subject, lob) || lob.equals(subject.lob()))) {
            return true;
        }
        return roles.contains("FUNDING_APPROVER") && coversLob(subject, lob);
    }

    private static boolean coversLob(Subject subject, String lob) {
        return subject.coveringLobs().contains(lob);
    }
}
