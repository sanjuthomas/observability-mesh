package com.srecatalog.common.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SubjectTest {

    @Test
    void displayNameFormatsFamilyGivenAndUserId() {
        Subject subject = new Subject("mo-100", "Sarah", "Chen", "Analyst", null,
                List.of("INSTRUCTION_CREATOR"), List.of("MIDDLE_OFFICE"), "mo-050", List.of(), null, List.of());
        assertThat(subject.displayName()).isEqualTo("Chen, Sarah (mo-100)");
    }

    @Test
    void toOpaSubjectIncludesOptionalFields() {
        Subject subject = new Subject("ficc-300", "Elena", "Vasquez", "Vice President", "FICC",
                List.of("INSTRUCTION_APPROVER"), List.of(), "ficc-400", List.of(), null, List.of());
        Map<String, Object> opa = subject.toOpaSubject();
        assertThat(opa).containsEntry("user_id", "ficc-300");
        assertThat(opa).containsEntry("lob", "FICC");
        assertThat(opa).containsEntry("supervisor_id", "ficc-400");
    }

    @Test
    void displayNameFallsBackToUserId() {
        Subject subject = new Subject("mo-100", null, null, "Analyst", null,
                List.of("INSTRUCTION_CREATOR"), List.of(), null, List.of(), null, List.of());
        assertThat(subject.displayName()).isEqualTo("mo-100");
        assertThat(subject.userIdJson()).isEqualTo("mo-100");
    }

    @Test
    void toOpaSubjectOmitsNullOptionalFields() {
        Subject subject = new Subject("mo-100", "Sarah", "Chen", "Analyst", null,
                List.of("INSTRUCTION_CREATOR"), List.of("MIDDLE_OFFICE"), null, List.of("FICC"),
                "admin-001", List.of("PLATFORM_ADMIN"));
        Map<String, Object> opa = subject.toOpaSubject();
        assertThat(opa).doesNotContainKey("lob");
        assertThat(opa).doesNotContainKey("supervisor_id");
        assertThat(opa).containsEntry("groups", List.of("MIDDLE_OFFICE"));
        assertThat(opa).containsEntry("covering_lobs", List.of("FICC"));
        assertThat(opa).containsEntry("delegated_by_roles", List.of("PLATFORM_ADMIN"));
    }

    @Test
    void displayNameWithPartialNames() {
        Subject givenOnly = new Subject("mo-100", "Sarah", "", "Analyst", null,
                List.of("INSTRUCTION_CREATOR"), List.of(), null, List.of(), null, List.of());
        Subject familyOnly = new Subject("mo-100", "", "Chen", "Analyst", null,
                List.of("INSTRUCTION_CREATOR"), List.of(), null, List.of(), null, List.of());
        assertThat(givenOnly.displayName()).isEqualTo("mo-100");
        assertThat(familyOnly.displayName()).isEqualTo("mo-100");
    }

    @Test
    void constructorNormalizesNullCollections() {
        Subject subject = new Subject("mo-100", "Sarah", "Chen", "Analyst", null,
                List.of("INSTRUCTION_CREATOR"), null, null, null, null, null);
        assertThat(subject.groups()).isEmpty();
        assertThat(subject.coveringLobs()).isEmpty();
        assertThat(subject.delegatedByRoles()).isEmpty();
    }

    @Test
    void constructorCopiesRoleAndGroupLists() {
        List<String> roles = new java.util.ArrayList<>(List.of("INSTRUCTION_CREATOR"));
        Subject subject = new Subject("mo-100", "Sarah", "Chen", "Analyst", null,
                roles, List.of("MIDDLE_OFFICE"), null, List.of(), null, List.of());
        roles.add("EXTRA");
        assertThat(subject.roles()).containsExactly("INSTRUCTION_CREATOR");
    }
}
