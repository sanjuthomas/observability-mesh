package com.observabilitymesh.ofac.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonConfigTest {

    @Test
    void objectMapperBeanCreated() {
        JacksonConfig config = new JacksonConfig();
        assertThat(config.objectMapper()).isNotNull();
    }
}
