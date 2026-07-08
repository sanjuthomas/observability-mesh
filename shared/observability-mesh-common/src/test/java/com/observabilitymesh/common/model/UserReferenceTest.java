package com.observabilitymesh.common.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UserReferenceTest {

    @Test
    void exposesJsonPropertyNames() {
        UserReference reference = new UserReference("mo-100", "Sarah", "Chen", "Analyst", "FICC",
                List.of("INSTRUCTION_CREATOR"), "mo-050");
        assertThat(reference.userId()).isEqualTo("mo-100");
        assertThat(reference.givenName()).isEqualTo("Sarah");
        assertThat(reference.supervisorId()).isEqualTo("mo-050");
    }
}
