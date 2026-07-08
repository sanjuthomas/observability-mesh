package com.observabilitymesh.sloprovisioner.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentHasherTest {

    @Test
    void sha256IsDeterministic() {
        ContentHasher hasher = new ContentHasher();
        assertThat(hasher.sha256("hello")).isEqualTo(hasher.sha256("hello"));
        assertThat(hasher.sha256("hello")).isNotEqualTo(hasher.sha256("world"));
    }
}
