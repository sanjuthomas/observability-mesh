package com.observabilitymesh.common.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SeedUserTest {

    @Test
    void normalizesNullCollections() {
        SeedUser user = new SeedUser("mo-100", "Sarah", "Chen", "Analyst",
                List.of("INSTRUCTION_CREATOR"), "FICC", null, "mo-050", null);
        assertThat(user.groups()).isEmpty();
        assertThat(user.coveringLobs()).isEmpty();
    }

    @Test
    void toSubjectMapsIdentityFields() {
        SeedUser user = new SeedUser("mo-100", "Sarah", "Chen", "Analyst",
                List.of("INSTRUCTION_CREATOR"), null, List.of("MIDDLE_OFFICE"), "mo-050", List.of());
        Subject subject = user.toSubject();
        assertThat(subject.userId()).isEqualTo("mo-100");
        assertThat(subject.roles()).containsExactly("INSTRUCTION_CREATOR");
        assertThat(subject.groups()).containsExactly("MIDDLE_OFFICE");
        assertThat(subject.supervisorId()).isEqualTo("mo-050");
    }

    @Test
    void copiesProvidedCollections() {
        List<String> roles = new java.util.ArrayList<>(List.of("INSTRUCTION_CREATOR"));
        SeedUser user = new SeedUser("mo-100", "Sarah", "Chen", "Analyst",
                roles, "FICC", List.of("MIDDLE_OFFICE"), null, List.of("FX"));
        roles.add("EXTRA");
        assertThat(user.roles()).containsExactly("INSTRUCTION_CREATOR");
        assertThat(user.coveringLobs()).containsExactly("FX");
    }
}
