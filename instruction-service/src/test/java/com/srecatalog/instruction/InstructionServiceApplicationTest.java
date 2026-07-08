package com.srecatalog.instruction;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import static org.assertj.core.api.Assertions.assertThat;

class InstructionServiceApplicationTest {

    @Test
    void applicationClassIsConfiguredForBoot() {
        assertThat(InstructionServiceApplication.class.isAnnotationPresent(SpringBootApplication.class))
                .isTrue();
        assertThat(InstructionServiceApplication.class.isAnnotationPresent(ConfigurationPropertiesScan.class))
                .isTrue();
    }
}
