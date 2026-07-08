package com.srecatalog.harness.service;

import com.srecatalog.auth.RequestSubjectResolver;
import com.srecatalog.common.model.Subject;
import com.srecatalog.common.web.PermissionDeniedException;
import com.srecatalog.harness.model.SessionCredentials;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class HarnessAdminAccess {

    private final RequestSubjectResolver subjectResolver;

    public HarnessAdminAccess(RequestSubjectResolver subjectResolver) {
        this.subjectResolver = subjectResolver;
    }

    public Subject requireAdmin(HttpServletRequest request) {
        Subject subject = subjectResolver.resolveActor(request);
        if (!subject.roles().contains("PLATFORM_ADMIN")) {
            throw new PermissionDeniedException("PLATFORM_ADMIN role required");
        }
        return subject;
    }

    public SessionCredentials requireAdminSession(HttpServletRequest request) {
        requireAdmin(request);
        String sessionId = subjectResolver.sessionId(request);
        if (sessionId == null || sessionId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Authorization Bearer token and X-Session-Id required");
        }
        String token = subjectResolver.bearerToken(request);
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authorization Bearer token required");
        }
        return new SessionCredentials(sessionId, token);
    }
}
