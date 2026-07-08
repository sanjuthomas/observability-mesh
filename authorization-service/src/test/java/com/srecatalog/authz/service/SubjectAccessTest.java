package com.srecatalog.authz.service;

import com.srecatalog.authz.config.AuthzProperties;
import com.srecatalog.common.model.Subject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubjectAccessTest {

    private final AuthzProperties properties = new AuthzProperties(
            "http://opa:9181", "/tmp/users.yaml", "COMPLIANCE_ANALYST,COMPLIANCE_OFFICER,PLATFORM_ADMIN");

    @Test
    void platformAdminPassesAdminCheck() {
        Subject admin = subject(List.of("PLATFORM_ADMIN"));
        SubjectAccess.requirePlatformAdmin(admin);
        assertThat(SubjectAccess.isPlatformAdmin(admin)).isTrue();
    }

    @Test
    void nonAdminRejected() {
        Subject user = subject(List.of("INSTRUCTION_CREATOR"));
        assertThatThrownBy(() -> SubjectAccess.requirePlatformAdmin(user))
                .hasMessageContaining("PLATFORM_ADMIN");
    }

    @Test
    void complianceSubjectIncludesAnalystAndAdmin() {
        assertThat(SubjectAccess.isComplianceSubject(subject(List.of("COMPLIANCE_ANALYST")), properties)).isTrue();
        assertThat(SubjectAccess.isComplianceSubject(subject(List.of("PLATFORM_ADMIN")), properties)).isTrue();
        assertThat(SubjectAccess.isComplianceSubject(subject(List.of("INSTRUCTION_CREATOR")), properties)).isFalse();
    }

    @Test
    void requireComplianceAllowsAnalyst() {
        SubjectAccess.requireCompliance(subject(List.of("COMPLIANCE_ANALYST")), properties);
    }

    @Test
    void requireComplianceRejectsNonComplianceUser() {
        assertThatThrownBy(() -> SubjectAccess.requireCompliance(subject(List.of("INSTRUCTION_CREATOR")), properties))
                .hasMessageContaining("COMPLIANCE_ANALYST");
    }

    private static Subject subject(List<String> roles) {
        return new Subject("u-1", "A", "B", "Title", "FICC", roles, List.of(), null, List.of("FICC"), null, List.of());
    }
}
