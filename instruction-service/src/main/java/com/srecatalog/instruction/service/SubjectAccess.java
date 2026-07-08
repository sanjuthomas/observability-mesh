package com.srecatalog.instruction.service;

import com.srecatalog.common.model.Subject;
import com.srecatalog.instruction.config.InstructionProperties;
import com.srecatalog.instruction.model.CashSettlementInstruction;

import java.util.Set;

public final class SubjectAccess {

    private SubjectAccess() {
    }

    public static boolean isPlatformAdmin(Subject subject) {
        return subject.roles().contains("PLATFORM_ADMIN");
    }

    public static boolean isComplianceSubject(Subject subject, InstructionProperties properties) {
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

    public static void requireCompliance(Subject subject, InstructionProperties properties) {
        if (!isComplianceSubject(subject, properties)) {
            throw new com.srecatalog.common.web.PermissionDeniedException(
                    "COMPLIANCE_ANALYST role required for policy inquiry");
        }
    }

    public static boolean canViewInstruction(Subject subject, CashSettlementInstruction instruction) {
        if (isPlatformAdmin(subject)) {
            return true;
        }
        if (subject.userId().equals(instruction.createdBy().userId())) {
            return true;
        }
        String lob = instruction.owningLob();
        Set<String> roles = Set.copyOf(subject.roles());
        if (roles.contains("INSTRUCTION_CREATOR") && (coversLob(subject, lob) || lob.equals(subject.lob()))) {
            return true;
        }
        return roles.contains("INSTRUCTION_APPROVER") && coversLob(subject, lob);
    }

    private static boolean coversLob(Subject subject, String lob) {
        return subject.coveringLobs().contains(lob);
    }
}
