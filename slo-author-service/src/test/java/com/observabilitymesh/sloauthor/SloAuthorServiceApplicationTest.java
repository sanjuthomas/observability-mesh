package com.observabilitymesh.sloauthor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import static org.assertj.core.api.Assertions.assertThat;

class SloAuthorServiceApplicationTest {

    @Test
    void applicationClassIsConfiguredForBoot() {
        assertThat(SloAuthorServiceApplication.class.isAnnotationPresent(SpringBootApplication.class))
            .isTrue();
    }
}
