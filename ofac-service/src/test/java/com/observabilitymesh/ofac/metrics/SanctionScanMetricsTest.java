package com.observabilitymesh.ofac.metrics;

import com.observabilitymesh.ofac.model.OfacScanResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SanctionScanMetricsTest {

    private SimpleMeterRegistry registry;
    private SanctionScanMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new SanctionScanMetrics(registry);
    }

    @Test
    void recordsFastDefinitiveScanWithDurationLeLabel() {
        metrics.recordCompletion(OfacScanResult.PASSED, Duration.ofSeconds(45));

        assertThat(count("PASSED", "60s")).isEqualTo(1.0);
        assertThat(count("PASSED", null)).isZero();
    }

    @Test
    void recordsSlowDefinitiveScanWithoutDurationLeLabel() {
        metrics.recordCompletion(OfacScanResult.FAILED, Duration.ofSeconds(90));

        assertThat(count("FAILED", null)).isEqualTo(1.0);
        assertThat(count("FAILED", "60s")).isZero();
    }

    @Test
    void recordsUnableToDetermineWithoutDurationLeLabel() {
        metrics.recordCompletion(OfacScanResult.UNABLE_TO_DETERMINE, Duration.ofSeconds(10));

        assertThat(count("UNABLE_TO_DETERMINE", null)).isEqualTo(1.0);
        assertThat(count("UNABLE_TO_DETERMINE", "60s")).isZero();
    }

    @Test
    void ignoresNullResultOrDuration() {
        metrics.recordCompletion(null, Duration.ofSeconds(1));
        metrics.recordCompletion(OfacScanResult.PASSED, null);
        metrics.recordCompletion(OfacScanResult.PASSED, Duration.ofSeconds(-1));

        assertThat(registry.getMeters()).isEmpty();
    }

    @Test
    void withinLatencyBudgetTreatsSixtySecondsAsGood() {
        assertThat(SanctionScanMetrics.withinLatencyBudget(OfacScanResult.PASSED, Duration.ofSeconds(60)))
                .isTrue();
        assertThat(SanctionScanMetrics.withinLatencyBudget(OfacScanResult.PASSED, Duration.ofSeconds(61)))
                .isFalse();
    }

    private double count(String result, String durationLe) {
        return registry.find(SanctionScanMetrics.METRIC_NAME).counters().stream()
                .filter(counter -> result.equals(counter.getId().getTag("result")))
                .filter(counter -> durationLe == null
                        ? counter.getId().getTag("duration_le") == null
                        : durationLe.equals(counter.getId().getTag("duration_le")))
                .mapToDouble(Counter::count)
                .sum();
    }
}
