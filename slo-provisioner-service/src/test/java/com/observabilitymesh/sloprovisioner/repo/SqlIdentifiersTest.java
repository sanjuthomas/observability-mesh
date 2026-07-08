package com.observabilitymesh.sloprovisioner.repo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqlIdentifiersTest {

    @Test
    void acceptsValidTableName() {
        assertThat(SqlIdentifiers.requireTableName("service_level_objectives"))
                .isEqualTo("service_level_objectives");
    }

    @Test
    void rejectsNullIdentifier() {
        assertThatThrownBy(() -> SqlIdentifiers.requireTableName(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnsafeIdentifier() {
        assertThatThrownBy(() -> SqlIdentifiers.requireTableName("service-level-objectives"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsafe SQL identifier");
    }
}
