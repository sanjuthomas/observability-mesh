package com.observabilitymesh.telemetry;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TelemetryAutoConfigurationTest {

    @Test
    void timedAspectUsesMeterRegistry() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TimedAspect aspect = new TelemetryAutoConfiguration().timedAspect(registry);
        assertThat(aspect).isNotNull();
    }
}
