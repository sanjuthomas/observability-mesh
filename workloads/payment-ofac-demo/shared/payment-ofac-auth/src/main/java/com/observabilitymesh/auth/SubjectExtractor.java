package com.observabilitymesh.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.observabilitymesh.common.model.Subject;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
public class SubjectExtractor {

    private final ObjectMapper objectMapper;

    public SubjectExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Subject fromJwt(Jwt jwt) {
        String userId = firstNonBlank(
                jwt.getClaimAsString("subject_user_id"),
                jwt.getClaimAsString("preferred_username"),
                jwt.getSubject());
        String title = jwt.getClaimAsString("title");
        if (title == null || title.isBlank()) {
            title = "Unknown";
        }
        return new Subject(
                userId,
                jwt.getClaimAsString("given_name"),
                jwt.getClaimAsString("family_name"),
                title,
                jwt.getClaimAsString("lob"),
                readStringList(jwt, "roles"),
                readStringList(jwt, "groups"),
                jwt.getClaimAsString("supervisor_id"),
                readStringList(jwt, "covering_lobs"),
                jwt.getClaimAsString("delegated_by"),
                readStringList(jwt, "delegated_by_roles"));
    }

    @SuppressWarnings("unchecked")
    private List<String> readStringList(Jwt jwt, String claim) {
        Object value = jwt.getClaim(claim);
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (value instanceof String raw) {
            if (raw.startsWith("[")) {
                try {
                    return objectMapper.readValue(raw, new TypeReference<List<String>>() {});
                } catch (Exception ignored) {
                    return List.of(raw);
                }
            }
            return raw.isBlank() ? List.of() : List.of(raw);
        }
        return List.of();
    }

    public Subject fromOnBehalfOfToken(String token) {
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid JWT");
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> claims = objectMapper.readValue(payload, new TypeReference<>() {});
            String userId = firstNonBlank(
                    asString(claims.get("subject_user_id")),
                    asString(claims.get("preferred_username")),
                    asString(claims.get("sub")));
            return new Subject(
                    userId,
                    asString(claims.get("given_name")),
                    asString(claims.get("family_name")),
                    firstNonBlank(asString(claims.get("title")), "Unknown"),
                    asString(claims.get("lob")),
                    readClaimList(claims.get("roles")),
                    readClaimList(claims.get("groups")),
                    asString(claims.get("supervisor_id")),
                    readClaimList(claims.get("covering_lobs")),
                    asString(claims.get("delegated_by")),
                    readClaimList(claims.get("delegated_by_roles")));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid OBO JWT", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> readClaimList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (value instanceof String raw) {
            if (raw.startsWith("[")) {
                try {
                    return objectMapper.readValue(raw, new TypeReference<List<String>>() {});
                } catch (Exception ignored) {
                    return List.of(raw);
                }
            }
            return raw.isBlank() ? List.of() : List.of(raw);
        }
        return List.of();
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "unknown";
    }
}
