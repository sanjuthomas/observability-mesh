package com.observabilitymesh.sloprovisioner.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JacksonConfigTest {

    @Test
    void objectMapperBeanCreated() {
        assertThat(new JacksonConfig().objectMapper()).isNotNull();
    }
}
