package com.observabilitymesh.telemetry;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.aop.TimedAspect;

@AutoConfiguration
public class TelemetryAutoConfiguration {

    @Bean
    TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }
}
