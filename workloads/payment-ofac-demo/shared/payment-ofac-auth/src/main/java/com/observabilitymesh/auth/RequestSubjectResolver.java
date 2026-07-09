package com.observabilitymesh.auth;

import com.observabilitymesh.common.model.Subject;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class RequestSubjectResolver {

    public static final String OBO_HEADER = "X-On-Behalf-Of";
    public static final String SESSION_HEADER = "X-Session-Id";
    public static final String OBO_SESSION_HEADER = "X-On-Behalf-Of-Session-Id";

    private final SubjectExtractor subjectExtractor;

    public RequestSubjectResolver(SubjectExtractor subjectExtractor) {
        this.subjectExtractor = subjectExtractor;
    }

    public Subject resolveActor(HttpServletRequest request) {
        String obo = request.getHeader(OBO_HEADER);
        if (obo != null && !obo.isBlank()) {
            return subjectExtractor.fromOnBehalfOfToken(stripBearer(obo));
        }
        return subjectFromAuthentication();
    }

    public Subject resolveCaller(HttpServletRequest request) {
        return subjectFromAuthentication();
    }

    public String bearerToken(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getTokenValue();
        }
        return null;
    }

    public String sessionId(HttpServletRequest request) {
        String session = request.getHeader(SESSION_HEADER);
        return session == null ? "" : session;
    }

    private Subject subjectFromAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return subjectExtractor.fromJwt(jwtAuth.getToken());
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing authentication");
    }

    private static String stripBearer(String token) {
        return token.startsWith("Bearer ") ? token.substring(7) : token;
    }

    public Jwt currentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing authentication");
    }
}
