package com.observabilitymesh.authz.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.observabilitymesh.auth.SubjectExtractor;
import com.observabilitymesh.authz.web.dto.SubjectPayload;
import com.observabilitymesh.common.model.Subject;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluateSubjectResolverTest {

    private final EvaluateSubjectResolver resolver = new EvaluateSubjectResolver(new SubjectExtractor(new ObjectMapper()));

    @Test
    void resolvesInlineSubjectWhenNoObo() {
        SubjectPayload payload = new SubjectPayload(
                "mo-100", "Sarah", "Chen", "Analyst", null,
                List.of("INSTRUCTION_CREATOR"), List.of("MIDDLE_OFFICE"), null, List.of(), null, List.of());
        Subject serviceCaller = serviceCaller();

        Subject subject = resolver.resolve(serviceCaller, null, payload);

        assertThat(subject.userId()).isEqualTo("mo-100");
        assertThat(subject.delegatedBy()).isNull();
    }

    @Test
    void resolvesOboSubjectWithDelegation() throws Exception {
        String token = oboToken(Map.of(
                "preferred_username", "mo-100",
                "title", "Analyst",
                "roles", List.of("INSTRUCTION_CREATOR"),
                "groups", List.of("MIDDLE_OFFICE")));
        Subject serviceCaller = serviceCaller();

        Subject subject = resolver.resolve(serviceCaller, token, null);

        assertThat(subject.userId()).isEqualTo("mo-100");
        assertThat(subject.delegatedBy()).isEqualTo("svc-instruction");
        assertThat(subject.delegatedByRoles()).containsExactly("SERVICE");
    }

    @Test
    void stripsBearerPrefixFromOboToken() throws Exception {
        String token = "Bearer " + oboToken(Map.of(
                "preferred_username", "mo-100",
                "title", "Analyst",
                "roles", List.of("INSTRUCTION_CREATOR"),
                "groups", List.of("MIDDLE_OFFICE")));

        Subject subject = resolver.resolve(serviceCaller(), token, null);
        assertThat(subject.userId()).isEqualTo("mo-100");
    }

    @Test
    void requiresSubjectWhenOboMissing() {
        assertThatThrownBy(() -> resolver.resolve(serviceCaller(), null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("subject is required");
    }

    private static Subject serviceCaller() {
        return new Subject(
                "svc-instruction", null, null, "Service", null,
                List.of("SERVICE"), List.of(), null, List.of(), null, List.of());
    }

    private static String oboToken(Map<String, Object> claims) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mapper.writeValueAsBytes(claims));
        return "header." + payload + ".sig";
    }
}
