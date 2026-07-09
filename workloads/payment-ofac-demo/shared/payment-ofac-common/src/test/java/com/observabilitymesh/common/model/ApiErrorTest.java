package com.observabilitymesh.common.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiErrorTest {

    @Test
    void ofBuildsErrorPayload() {
        ApiError error = ApiError.of(404, "Not Found", "missing", "/api/v1/items/1");
        assertThat(error.status()).isEqualTo(404);
        assertThat(error.error()).isEqualTo("Not Found");
        assertThat(error.message()).isEqualTo("missing");
        assertThat(error.path()).isEqualTo("/api/v1/items/1");
        assertThat(error.timestamp()).isNotNull();
    }
}
