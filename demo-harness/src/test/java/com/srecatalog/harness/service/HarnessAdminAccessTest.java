package com.srecatalog.harness.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.srecatalog.auth.RequestSubjectResolver;
import com.srecatalog.common.web.PermissionDeniedException;
import com.srecatalog.harness.HarnessTestFixtures;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HarnessAdminAccessTest {

    @Mock RequestSubjectResolver subjectResolver;
    @Mock HttpServletRequest request;

    @Test
    void requireAdminRejectsNonAdmin() {
        HarnessAdminAccess access = new HarnessAdminAccess(subjectResolver);
        when(subjectResolver.resolveActor(request)).thenReturn(
                new com.srecatalog.common.model.Subject(
                        "mo-100", "Sarah", "Chen", "Analyst", null,
                        java.util.List.of("INSTRUCTION_CREATOR"), java.util.List.of(), null,
                        java.util.List.of(), null, java.util.List.of()));

        assertThatThrownBy(() -> access.requireAdmin(request))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void requireAdminSessionRequiresSessionHeader() {
        HarnessAdminAccess access = new HarnessAdminAccess(subjectResolver);
        when(subjectResolver.resolveActor(request)).thenReturn(HarnessTestFixtures.ADMIN);
        when(subjectResolver.sessionId(request)).thenReturn("");

        assertThatThrownBy(() -> access.requireAdminSession(request))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void requireAdminSessionRequiresBearerToken() {
        HarnessAdminAccess access = new HarnessAdminAccess(subjectResolver);
        when(subjectResolver.resolveActor(request)).thenReturn(HarnessTestFixtures.ADMIN);
        when(subjectResolver.sessionId(request)).thenReturn("sess-1");
        when(subjectResolver.bearerToken(request)).thenReturn("");

        assertThatThrownBy(() -> access.requireAdminSession(request))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void requireAdminSessionReturnsCredentials() {
        HarnessAdminAccess access = new HarnessAdminAccess(subjectResolver);
        when(subjectResolver.resolveActor(request)).thenReturn(HarnessTestFixtures.ADMIN);
        when(subjectResolver.sessionId(request)).thenReturn("sess-1");
        when(subjectResolver.bearerToken(request)).thenReturn("token-1");

        var session = access.requireAdminSession(request);

        assertThat(session.sessionId()).isEqualTo("sess-1");
        assertThat(session.sessionToken()).isEqualTo("token-1");
    }
}
