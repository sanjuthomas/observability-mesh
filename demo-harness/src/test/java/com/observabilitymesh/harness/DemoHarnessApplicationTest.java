package com.observabilitymesh.harness;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import static org.assertj.core.api.Assertions.assertThat;

class DemoHarnessApplicationTest {

    @Test
    void applicationClassIsConfiguredForBoot() {
        assertThat(DemoHarnessApplication.class.isAnnotationPresent(SpringBootApplication.class))
                .isTrue();
        assertThat(DemoHarnessApplication.class.isAnnotationPresent(ConfigurationPropertiesScan.class))
                .isTrue();
    }
}
