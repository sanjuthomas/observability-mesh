package com.observabilitymesh.ofac.service;

import com.observabilitymesh.common.model.Subject;
import com.observabilitymesh.common.web.PermissionDeniedException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubjectAccessTest {

    private final Subject admin = new Subject(
            "admin-001", "Admin", "User", "Platform Admin", "FICC",
            List.of("PLATFORM_ADMIN"), List.of(), null, List.of("FICC"), null, List.of());
    private final Subject user = new Subject(
            "user-001", "User", "One", "Analyst", "FICC",
            List.of("PAYMENT_CREATOR"), List.of(), null, List.of("FICC"), null, List.of());

    @Test
    void platformAdminRecognized() {
        assertThat(SubjectAccess.isPlatformAdmin(admin)).isTrue();
        assertThat(SubjectAccess.isPlatformAdmin(user)).isFalse();
    }

    @Test
    void requirePlatformAdminThrowsForNonAdmin() {
        assertThatThrownBy(() -> SubjectAccess.requirePlatformAdmin(user))
                .isInstanceOf(PermissionDeniedException.class);
    }
}
