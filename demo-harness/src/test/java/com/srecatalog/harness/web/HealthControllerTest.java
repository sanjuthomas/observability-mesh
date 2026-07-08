package com.srecatalog.harness.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HealthControllerTest {

    @Test
    void healthReturnsUp() {
        assertThat(new HealthController().health()).containsEntry("status", "UP");
    }
}
