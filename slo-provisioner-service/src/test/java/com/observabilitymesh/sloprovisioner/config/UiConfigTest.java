package com.observabilitymesh.sloprovisioner.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UiConfigTest {

    @Test
    void registersStaticHandler() {
        UiConfig config = new UiConfig();
        ResourceHandlerRegistry registry = mock(ResourceHandlerRegistry.class);
        ResourceHandlerRegistration registration = mock(ResourceHandlerRegistration.class);
        when(registry.addResourceHandler("/ui/static/**")).thenReturn(registration);
        when(registration.addResourceLocations(anyString())).thenReturn(registration);

        config.addResourceHandlers(registry);

        verify(registry).addResourceHandler("/ui/static/**");
        verify(registration).addResourceLocations("classpath:/static/");
    }
}
